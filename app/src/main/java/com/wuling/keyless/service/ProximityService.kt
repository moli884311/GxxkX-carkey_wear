package com.wuling.keyless.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.wuling.keyless.Constants
import com.wuling.keyless.ble.BleConnector
import com.wuling.keyless.ble.BleForegroundService
import com.wuling.keyless.ble.ProximityScanner
import com.wuling.keyless.storage.KeyStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DoorState { LOCKED, UNLOCKED, UNKNOWN }

enum class ConnectionState { SCANNING, CONNECTING, CONNECTED, DISCONNECTED }

class ProximityService(private val context: Context) {

    data class Status(
        val rssi: Int = -100,
        val label: String = "搜索中...",
        val isNear: Boolean = false,
        val isFar: Boolean = false,
        val doorState: DoorState = DoorState.UNKNOWN,
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val lastError: String? = null
    )

    private val storage = KeyStorage(context)
    private val connector = BleConnector(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private val _logs = MutableSharedFlow<String>(replay = 50)
    val logs = _logs.asSharedFlow()

    private var scanJob: Job? = null
    private var lastLockAction = 0L
    private var isRunning = false
    private var targetDevice: BluetoothDevice? = null
    private var retryCount = 0L

    private suspend fun log(msg: String) {
        _logs.emit("[${System.currentTimeMillis().toString().takeLast(8)}] $msg")
    }

    private fun backoffDelay(): Long {
        val delay = Constants.RETRY_BACKOFF_BASE_MS * (1L shl retryCount.toInt().coerceAtMost(5))
        return delay.coerceAtMost(Constants.RETRY_BACKOFF_MAX_MS)
    }

    suspend fun start() {
        if (isRunning) return
        isRunning = true

        val mac = storage.getMac() ?: return log("未配置 BLE MAC")
        val masterKey = storage.getMasterKey() ?: return log("未配置 masterKey")
        val masterRandom = storage.getMasterRandom() ?: return log("未配置 masterRandom")

        val scanner = ProximityScanner(context, mac)

        if (!scanner.hasBlePermission()) {
            log("缺少蓝牙权限")
            _status.value = _status.value.copy(lastError = "缺少蓝牙权限")
            return
        }

        if (!scanner.isBluetoothReady()) {
            log("蓝牙未开启")
            _status.value = _status.value.copy(lastError = "蓝牙未开启")
            return
        }

        targetDevice = scanner.getDevice()
        if (targetDevice == null) {
            log("无效的 MAC 地址: $mac")
            _status.value = _status.value.copy(lastError = "无效的 MAC 地址")
            return
        }

        startForegroundService()
        log("开始扫描车辆 BLE 信号...")
        _status.value = _status.value.copy(connectionState = ConnectionState.SCANNING)

        scanJob = scope.launch {
            try {
                scanner.startScanning().collect { state ->
                    _status.value = _status.value.copy(
                        rssi = state.rssi,
                        label = state.label,
                        isNear = state.isNear,
                        isFar = state.isFar
                    )
                    log("RSSI: ${state.rssi} dBm (${state.label})")
                    handleProximity(state, masterKey, masterRandom)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("扫描异常: ${e.message}")
                _status.value = _status.value.copy(lastError = "扫描异常: ${e.message}")
            }
        }
    }

    private suspend fun handleProximity(state: ProximityScanner.ProximityState, masterKey: String, masterRandom: String) {
        val now = System.currentTimeMillis() / 1000
        if (now - lastLockAction < Constants.LOCK_DEBOUNCE_SEC) return

        val autoUnlock = storage.isAutoUnlockEnabled()
        val autoLock = storage.isAutoLockEnabled()

        if (autoUnlock && state.isNear && _status.value.doorState != DoorState.UNLOCKED) {
            log("靠近车辆，自动开锁...")
            performUnlock(masterKey, masterRandom)
        } else if (autoLock && state.isFar && _status.value.doorState != DoorState.LOCKED) {
            log("远离车辆，自动落锁...")
            performLock(masterKey, masterRandom)
        }
    }

    suspend fun manualLock(): String {
        val mk = storage.getMasterKey() ?: return "未配置 masterKey"
        val mr = storage.getMasterRandom() ?: return "未配置 masterRandom"
        val result = performLock(mk, mr)
        return if (result) "上锁成功" else "上锁失败"
    }

    suspend fun manualUnlock(): String {
        val mk = storage.getMasterKey() ?: return "未配置 masterKey"
        val mr = storage.getMasterRandom() ?: return "未配置 masterRandom"
        val result = performUnlock(mk, mr)
        return if (result) "开锁成功" else "开锁失败"
    }

    suspend fun manualPark(): String {
        val mk = storage.getMasterKey() ?: return "未配置 masterKey"
        val mr = storage.getMasterRandom() ?: return "未配置 masterRandom"
        val device = targetDevice ?: return "未找到车辆设备"
        _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTING)
        log("泊车中...")
        return try {
            connector.connectAndWait(device, Constants.CONNECTION_TIMEOUT_SEC * 1000L)
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(500)
            val success = connector.sendUnlock(mk, mr)
            if (success) {
                _status.value = _status.value.copy(doorState = DoorState.UNLOCKED)
                log("泊车模式：已开锁")
                "泊车模式已开启"
            } else {
                "泊车指令发送失败"
            }
        } catch (e: Exception) {
            "连接失败: ${e.message}"
        } finally {
            connector.disconnectQuietly()
            _status.value = _status.value.copy(connectionState = ConnectionState.DISCONNECTED)
        }
    }

    private suspend fun performUnlock(masterKey: String, masterRandom: String): Boolean {
        val device = targetDevice ?: return false
        _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTING)
        try {
            connector.connectAndWait(device, Constants.CONNECTION_TIMEOUT_SEC * 1000L)
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(1000)
            if (connector.sendUnlock(masterKey, masterRandom)) {
                _status.value = _status.value.copy(doorState = DoorState.UNLOCKED, lastError = null)
                lastLockAction = System.currentTimeMillis() / 1000
                retryCount = 0
                log("开锁成功")
                return true
            } else {
                log("开锁失败")
                _status.value = _status.value.copy(lastError = "开锁失败")
                return false
            }
        } catch (e: Exception) {
            retryCount++
            val msg = "连接失败: ${e.message}"
            log(msg)
            if (retryCount >= Constants.MAX_RETRY_COUNT) {
                _status.value = _status.value.copy(lastError = "$msg (已达最大重试次数)")
                return false
            }
            val waitMs = backoffDelay()
            log("等待 ${waitMs / 1000}s 后第 ${retryCount} 次重试...")
            delay(waitMs)
            return performUnlock(masterKey, masterRandom)
        } finally {
            connector.disconnectQuietly()
            _status.value = _status.value.copy(connectionState = ConnectionState.DISCONNECTED)
        }
    }

    private suspend fun performLock(masterKey: String, masterRandom: String): Boolean {
        val device = targetDevice ?: return false
        _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTING)
        try {
            connector.connectAndWait(device, Constants.CONNECTION_TIMEOUT_SEC * 1000L)
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(1000)
            if (connector.sendLock(masterKey, masterRandom)) {
                _status.value = _status.value.copy(doorState = DoorState.LOCKED, lastError = null)
                lastLockAction = System.currentTimeMillis() / 1000
                retryCount = 0
                log("落锁成功")
                return true
            } else {
                log("落锁失败")
                _status.value = _status.value.copy(lastError = "落锁失败")
                return false
            }
        } catch (e: Exception) {
            retryCount++
            val msg = "连接失败: ${e.message}"
            log(msg)
            if (retryCount >= Constants.MAX_RETRY_COUNT) {
                _status.value = _status.value.copy(lastError = "$msg (已达最大重试次数)")
                return false
            }
            val waitMs = backoffDelay()
            log("等待 ${waitMs / 1000}s 后第 ${retryCount} 次重试...")
            delay(waitMs)
            return performLock(masterKey, masterRandom)
        } finally {
            connector.disconnectQuietly()
            _status.value = _status.value.copy(connectionState = ConnectionState.DISCONNECTED)
        }
    }

    suspend fun stop() {
        isRunning = false
        scanJob?.cancel()
        scanJob = null
        connector.disconnectQuietly()
        connector.close()
        stopForegroundService()
        log("服务已停止")
    }

    private fun startForegroundService() {
        try {
            val intent = Intent(context, BleForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProximityService", "启动前台服务失败: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            context.stopService(Intent(context, BleForegroundService::class.java))
        } catch (e: Exception) {
            // service already stopped
        }
    }
}
