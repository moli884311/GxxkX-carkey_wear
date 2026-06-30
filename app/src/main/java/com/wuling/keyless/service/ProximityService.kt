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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val startMutex = Mutex()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private val _logs = MutableSharedFlow<String>(replay = 50)
    val logs = _logs.asSharedFlow()

    private var scanJob: Job? = null
    private var disconnectTimer: Job? = null
    private var probeTimer: Job? = null
    private var lastLockAction = 0L
    private var lastDeviceSeen = 0L
    private var lastScanResult = 0L
    private var isRunning = false
    private var isProbing = false
    private var targetDevice: BluetoothDevice? = null
    private var retryCount = 0L
    private var cachedMasterKey: String? = null
    private var cachedMasterRandom: String? = null
    private var cachedBleKey: String? = null

    private suspend fun log(msg: String) {
        LogRepository.append("ProxSvc", msg)
        _logs.emit("[${System.currentTimeMillis().toString().takeLast(8)}] $msg")
    }

    private fun backoffDelay(): Long {
        val delay = Constants.RETRY_BACKOFF_BASE_MS * (1L shl retryCount.toInt().coerceAtMost(5))
        return delay.coerceAtMost(Constants.RETRY_BACKOFF_MAX_MS)
    }

    suspend fun start() {
        startMutex.withLock {
            if (isRunning) return
            isRunning = true

            val mac = storage.getMac() ?: run { isRunning = false; return log("未配置 BLE MAC") }
            val masterKey = storage.getMasterKey() ?: run { isRunning = false; return log("未配置 masterKey") }
            val masterRandom = storage.getMasterRandom() ?: run { isRunning = false; return log("未配置 masterRandom") }
            val bleKey = storage.getKey() ?: masterKey

            cachedMasterKey = masterKey
            cachedMasterRandom = masterRandom
            cachedBleKey = bleKey

            val scanner = ProximityScanner(context, mac)

            if (!scanner.hasBlePermission()) {
                log("缺少蓝牙权限")
                _status.value = _status.value.copy(lastError = "缺少蓝牙权限")
                isRunning = false
                return
            }

            if (!scanner.isBluetoothReady()) {
                log("蓝牙未开启")
                _status.value = _status.value.copy(lastError = "蓝牙未开启")
                isRunning = false
                return
            }

            targetDevice = scanner.getDevice()
            if (targetDevice == null) {
                log("无效的 MAC 地址: $mac")
                _status.value = _status.value.copy(lastError = "无效的 MAC 地址")
                isRunning = false
                return
            }

            startForegroundService()
            log("开始扫描车辆 BLE 信号...")
            _status.value = _status.value.copy(connectionState = ConnectionState.SCANNING)

            scanJob?.cancel()
            scanJob = scope.launch {
                try {
                    scanner.startScanning().collect { state ->
                        lastScanResult = System.currentTimeMillis()
                        lastDeviceSeen = lastScanResult
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

            disconnectTimer?.cancel()
            disconnectTimer = scope.launch {
                while (isActive) {
                    delay(5000)
                    if (connector.connected && lastDeviceSeen > 0 &&
                        System.currentTimeMillis() - lastDeviceSeen > 30_000
                    ) {
                        log("30秒未检测到设备，断开BLE连接")
                        connector.disconnectQuietly()
                        _status.value = _status.value.copy(connectionState = ConnectionState.SCANNING)
                    }
                }
            }

            probeTimer?.cancel()
            probeTimer = scope.launch {
                delay(8000)
                while (isActive) {
                    if (!connector.connected && !isProbing &&
                        System.currentTimeMillis() - lastScanResult > 7000
                    ) {
                        probeDevice(masterKey, masterRandom)
                    }
                    delay(8000)
                }
            }
        }
    }

    private suspend fun probeDevice(masterKey: String, masterRandom: String) {
        val device = targetDevice ?: return
        if (isProbing) return
        isProbing = true
        try {
            val mk = cachedMasterKey ?: masterKey
            val mr = cachedMasterRandom ?: masterRandom
            log("扫描无结果, 尝试直接探测连接...")
            val connected = connector.ensureConnected(device)
            if (connected) {
                lastDeviceSeen = System.currentTimeMillis()
                lastScanResult = lastDeviceSeen
                _status.value = _status.value.copy(
                    connectionState = ConnectionState.CONNECTED,
                    rssi = -50,
                    label = "已连接(直连)"
                )
                log("直接连接成功, 已建立持久连接")
                val state = ProximityScanner.ProximityState(rssi = -50, label = "已连接", isNear = true, isFar = false)
                handleProximity(state, mk, mr)
            } else {
                _status.value = _status.value.copy(rssi = -100, label = "搜索中...")
            }
        } catch (e: Exception) {
            log("直接探测异常: ${e.message}")
        } finally {
            isProbing = false
        }
    }

    private suspend fun handleProximity(
        state: ProximityScanner.ProximityState,
        masterKey: String,
        masterRandom: String
    ) {
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
        log("泊车中...")
        return try {
            val connected = connector.ensureConnected(device)
            if (!connected) throw RuntimeException("BLE连接失败")
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(500)
            if (connector.sendUnlock(cachedBleKey ?: mk, mk, mr)) {
                _status.value = _status.value.copy(doorState = DoorState.UNLOCKED)
                log("泊车模式：已开锁")
                "泊车模式已开启"
            } else {
                "泊车指令发送失败"
            }
        } catch (e: Exception) {
            "连接失败: ${e.message}"
        }
    }

    private suspend fun performUnlock(masterKey: String, masterRandom: String): Boolean {
        val device = targetDevice ?: return false
        lastLockAction = System.currentTimeMillis() / 1000
        _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTING)
        try {
            val connected = connector.ensureConnected(device)
            if (!connected) throw RuntimeException("BLE连接失败")
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(2000)
            if (connector.sendUnlock(cachedBleKey ?: masterKey, masterKey, masterRandom)) {
                _status.value = _status.value.copy(doorState = DoorState.UNLOCKED, lastError = null)
                retryCount = 0
                log("开锁成功")
                lastDeviceSeen = System.currentTimeMillis()
                lastScanResult = lastDeviceSeen
                return true
            }
            log("开锁失败")
            _status.value = _status.value.copy(lastError = "开锁失败")
            return false
        } catch (e: Exception) {
            retryCount++
            connector.connected = false
            val msg = "连接失败: ${e.message}"
            log(msg)
            if (retryCount >= Constants.MAX_RETRY_COUNT) {
                _status.value = _status.value.copy(
                    lastError = "$msg (已达最大重试次数)",
                    connectionState = ConnectionState.DISCONNECTED
                )
                return false
            }
            val waitMs = backoffDelay()
            log("等待 ${waitMs / 1000}s 后第 ${retryCount} 次重试...")
            delay(waitMs)
            return performUnlock(masterKey, masterRandom)
        }
    }

    private suspend fun performLock(masterKey: String, masterRandom: String): Boolean {
        val device = targetDevice ?: return false
        lastLockAction = System.currentTimeMillis() / 1000
        _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTING)
        try {
            val connected = connector.ensureConnected(device)
            if (!connected) throw RuntimeException("BLE连接失败")
            _status.value = _status.value.copy(connectionState = ConnectionState.CONNECTED)
            delay(2000)
            if (connector.sendLock(cachedBleKey ?: masterKey, masterKey, masterRandom)) {
                _status.value = _status.value.copy(doorState = DoorState.LOCKED, lastError = null)
                retryCount = 0
                log("落锁成功")
                lastDeviceSeen = System.currentTimeMillis()
                lastScanResult = lastDeviceSeen
                return true
            }
            log("落锁失败")
            _status.value = _status.value.copy(lastError = "落锁失败")
            return false
        } catch (e: Exception) {
            retryCount++
            connector.connected = false
            val msg = "连接失败: ${e.message}"
            log(msg)
            if (retryCount >= Constants.MAX_RETRY_COUNT) {
                _status.value = _status.value.copy(
                    lastError = "$msg (已达最大重试次数)",
                    connectionState = ConnectionState.DISCONNECTED
                )
                return false
            }
            val waitMs = backoffDelay()
            log("等待 ${waitMs / 1000}s 后第 ${retryCount} 次重试...")
            delay(waitMs)
            return performLock(masterKey, masterRandom)
        }
    }

    suspend fun stop() {
        startMutex.withLock {
            isRunning = false
            scanJob?.cancel()
            scanJob = null
            disconnectTimer?.cancel()
            disconnectTimer = null
            probeTimer?.cancel()
            probeTimer = null
            connector.disconnectQuietly()
            try { connector.close() } catch (_: Exception) {}
            stopForegroundService()
            log("服务已停止")
        }
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
        }
    }
}
