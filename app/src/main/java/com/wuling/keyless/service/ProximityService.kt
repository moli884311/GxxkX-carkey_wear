package com.wuling.keyless.service

import android.content.Context
import com.wuling.keyless.Constants
import com.wuling.keyless.ble.BleConnector
import com.wuling.keyless.ble.ProximityScanner
import com.wuling.keyless.storage.KeyStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DoorState { LOCKED, UNLOCKED, UNKNOWN }

class ProximityService(private val context: Context) {

    data class Status(
        val rssi: Int = -100,
        val label: String = "搜索中...",
        val isNear: Boolean = false,
        val isFar: Boolean = false,
        val doorState: DoorState = DoorState.UNKNOWN
    )

    private val storage = KeyStorage(context)
    private val connector = BleConnector()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private val _logs = MutableSharedFlow<String>(replay = 50)
    val logs = _logs.asSharedFlow()

    private var scanJob: Job? = null
    private var lastLockAction = 0L

    private suspend fun log(msg: String) {
        _logs.emit("[${System.currentTimeMillis().toString().takeLast(10)}] $msg")
    }

    suspend fun start() {
        val mac = storage.getMac() ?: return log("未配置 BLE MAC")
        val key = storage.getKey() ?: return log("未配置 BLE 密钥")
        val scanner = ProximityScanner(context, mac)

        log("开始扫描车辆...")
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            scanner.startScanning().collect { state ->
                _status.value = Status(
                    rssi = state.rssi,
                    label = state.label,
                    isNear = state.isNear,
                    isFar = state.isFar,
                    doorState = _status.value.doorState
                )
                log("RSSI: ${state.rssi} dBm (${state.label})")
                handleProximity(state, mac, key)
            }
        }
    }

    private suspend fun handleProximity(state: ProximityScanner.ProximityState, mac: String, key: String) {
        val now = System.currentTimeMillis() / 1000
        if (now - lastLockAction < Constants.LOCK_DEBOUNCE_SEC) return

        val autoUnlock = storage.isAutoUnlockEnabled()
        val autoLock = storage.isAutoLockEnabled()

        if (autoUnlock && state.isNear && _status.value.doorState != DoorState.UNLOCKED) {
            log("靠近车辆，自动开锁...")
            connector.connect(mac)
            try {
                delay(1000)
                if (connector.sendUnlock(key)) {
                    _status.value = _status.value.copy(doorState = DoorState.UNLOCKED)
                    lastLockAction = now
                    log("开锁成功")
                } else {
                    log("开锁失败")
                }
            } finally {
                connector.disconnect()
            }
        } else if (autoLock && state.isFar && _status.value.doorState != DoorState.LOCKED) {
            log("远离车辆，自动落锁...")
            connector.connect(mac)
            try {
                delay(1000)
                if (connector.sendLock(key)) {
                    _status.value = _status.value.copy(doorState = DoorState.LOCKED)
                    lastLockAction = now
                    log("落锁成功")
                } else {
                    log("落锁失败")
                }
            } finally {
                connector.disconnect()
            }
        }
    }

    suspend fun stop() {
        scanJob?.cancel()
        connector.disconnect()
        connector.close()
    }
}
