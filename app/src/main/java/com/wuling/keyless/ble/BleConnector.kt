package com.wuling.keyless.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.wuling.keyless.Constants
import com.wuling.keyless.service.LogRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import pet.morning.linkey.BleCommand
import pet.morning.linkey.NativeLib
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BleConnector(context: Context) : BleManager(context) {

    private val nativeLib = NativeLib()
    private var cachedGatt: BluetoothGatt? = null
    private var sessionReady = false

    @Volatile
    var connected: Boolean = false
        internal set

    suspend fun connectAndWait(device: BluetoothDevice, timeoutMs: Long = 10_000): Boolean =
        suspendCancellableCoroutine { cont ->
            var resolved = false
            connect(device)
                .timeout(timeoutMs)
                .done {
                    if (!resolved) { resolved = true; connected = true; LogRepository.append("BLE", "连接成功 ${device.address}") }
                    cont.resume(true)
                }
                .fail { _, status ->
                    if (!resolved) { resolved = true; LogRepository.append("BLE", "连接失败: $status") }
                    cont.resumeWithException(RuntimeException("BLE连接失败: $status"))
                }
                .enqueue()
            cont.invokeOnCancellation {
                if (!resolved) disconnect()
            }
        }

    suspend fun ensureConnected(device: BluetoothDevice): Boolean {
        if (connected) return true
        return try {
            connectAndWait(device)
        } catch (_: Exception) {
            false
        }
    }

    fun disconnectQuietly() {
        try {
            try { nativeLib.destroySession() } catch (_: Exception) {}
            sessionReady = false
            disconnect().enqueue()
            connected = false
            LogRepository.append("BLE", "已断开连接")
        } catch (_: Exception) {}
    }

    suspend fun enableNotifications(): BluetoothGattCharacteristic? =
        suspendCancellableCoroutine { cont ->
            val char = getNotifyCharacteristic()
            if (char == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            enableIndications(char)
                .done { if (cont.isActive) cont.resume(char) }
                .fail { _, status ->
                    if (cont.isActive) cont.resumeWithException(RuntimeException("启用通知失败: $status"))
                }
                .enqueue()
        }

    fun observeNotifications(): Flow<ByteArray> = callbackFlow {
        val char = getNotifyCharacteristic()
        if (char == null) {
            close()
            return@callbackFlow
        }
        setNotificationCallback(char).with { _, data: Data ->
            trySend(data.getValue() ?: ByteArray(0))
        }
        awaitClose {
            try { removeNotificationCallback(char) } catch (_: Exception) {}
        }
    }

    suspend fun initSession(masterKeyHex: String, masterRandomHex: String) {
        try {
            val keyBytes = hexToBytes(masterKeyHex)
            val randomBytes = hexToBytes(masterRandomHex)
            nativeLib.initAuth("", keyBytes, randomBytes)
            sessionReady = true
            LogRepository.append("BLE", "Native auth session initialized rawKeyLen=${masterKeyHex.length} rawRandLen=${masterRandomHex.length}")
        } catch (e: Exception) {
            sessionReady = false
            LogRepository.append("BLE", "initAuth failed: ${e.message}")
            throw e
        }
    }

    suspend fun sendLock(bleKeyHex: String, masterKeyHex: String, masterRandomHex: String): Boolean {
        if (!sessionReady) initSession(masterKeyHex, masterRandomHex)
        return performNativeControl("START_PARK_2")
    }

    suspend fun sendUnlock(bleKeyHex: String, masterKeyHex: String, masterRandomHex: String): Boolean {
        if (!sessionReady) initSession(masterKeyHex, masterRandomHex)
        return performNativeControl("START_OUT_2")
    }

    private suspend fun performNativeControl(intent: String): Boolean {
        val writeChar = getWriteCharacteristic() ?: return false
        try {
            nativeLib.setControlIntent(intent)
            LogRepository.append("BLE", "Control intent: $intent")

            if (!setupIndicationsAndWait()) return false

            var round = 0
            while (round < 10) {
                round++
                val cmd: BleCommand = nativeLib.getNextCommand() ?: break
                val data = cmd.data ?: break

                LogRepository.append("BLE", "Phase write round=$round len=${data.size} hex=${data.joinToString("") { "%02x".format(it) }}")
                val ok = writeAndWait(writeChar, data)
                if (!ok) {
                    LogRepository.append("BLE", "Write failed at round $round")
                    return false
                }

                val resp = waitForIndication(3000L)
                if (resp != null && resp.isNotEmpty()) {
                    LogRepository.append("BLE", "Response round=$round src=$_indicationSource len=${resp.size} hex=${resp.joinToString("") { "%02x".format(it) }}")
                    nativeLib.feedNotification(resp)
                } else {
                    LogRepository.append("BLE", "No response at round $round")
                }
            }

            LogRepository.append("BLE", "Control $intent completed ($round rounds)")
            return round > 0
        } catch (e: Exception) {
            LogRepository.append("BLE", "Native control $intent failed: ${e.message}")
            return false
        }
    }

    suspend fun setupIndicationsAndWait(): Boolean {
        val notifyChar = getNotifyCharacteristic() ?: return false
        val writeChar = getWriteCharacteristic() ?: return false

        _lastIndication = null
        _indicationSource = null

        setIndicationCallback(notifyChar).with { _, data ->
            _lastIndication = data.getValue()
            _indicationSource = "NOTIFY"
        }
        setIndicationCallback(writeChar).with { _, data ->
            _lastIndication = data.getValue()
            _indicationSource = "WRITE"
        }

        return suspendCancellableCoroutine { cont ->
            enableIndications(notifyChar)
                .done {
                    enableIndications(writeChar)
                        .done { if (cont.isActive) cont.resume(true) }
                        .fail { _, _ -> if (cont.isActive) cont.resume(true) }
                        .enqueue()
                }
                .fail { _, _ ->
                    enableIndications(writeChar)
                        .done { if (cont.isActive) cont.resume(true) }
                        .fail { _, _ -> if (cont.isActive) cont.resume(true) }
                        .enqueue()
                }
                .enqueue()
        }
    }

    private suspend fun writeAndWait(writeChar: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        return suspendCancellableCoroutine<Boolean> { cont ->
            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            writeCharacteristic(writeChar, data)
                .done { if (cont.isActive) cont.resume(true) }
                .fail { _, status ->
                    if (cont.isActive) cont.resume(false)
                    LogRepository.append("BLE", "写入失败 status=$status")
                }
                .enqueue()
        }
    }

    private suspend fun waitForIndication(timeoutMs: Long): ByteArray? {
        return withTimeoutOrNull(timeoutMs) {
            while (_lastIndication == null) {
                kotlinx.coroutines.delay(100)
            }
            _lastIndication.also { _lastIndication = null }
        }
    }

    @Volatile
    private var _lastIndication: ByteArray? = null

    @Volatile
    private var _indicationSource: String? = null

    fun getWriteCharacteristic(): BluetoothGattCharacteristic? {
        return findCharacteristic(Constants.BLE_WRITE_CHAR_UUID)
    }

    fun getNotifyCharacteristic(): BluetoothGattCharacteristic? {
        return findCharacteristic(Constants.BLE_NOTIFY_CHAR_UUID)
    }

    private fun findCharacteristic(uuidStr: String): BluetoothGattCharacteristic? {
        val services = cachedGatt?.services ?: return null
        val targetUuid = UUID.fromString(uuidStr)
        for (s in services) {
            if (s.uuid.toString().uppercase() == Constants.BLE_SERVICE_UUID.uppercase()) {
                return s.getCharacteristic(targetUuid)
            }
        }
        return null
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        cachedGatt = gatt
        return true
    }

    override fun onServicesInvalidated() {
        cachedGatt = null
        connected = false
    }
}
