package com.wuling.keyless.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.wuling.keyless.Constants
import com.wuling.keyless.service.LogRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BleConnector(context: Context) : BleManager(context) {

    private var cachedGatt: BluetoothGatt? = null

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

    suspend fun sendLock(bleKeyHex: String, masterKeyHex: String, masterRandomHex: String): Boolean =
        sendAuthenticatedCommand(Constants.CMD_LOCK, bleKeyHex, masterKeyHex, masterRandomHex)

    suspend fun sendUnlock(bleKeyHex: String, masterKeyHex: String, masterRandomHex: String): Boolean =
        sendAuthenticatedCommand(Constants.CMD_UNLOCK, bleKeyHex, masterKeyHex, masterRandomHex)

    private suspend fun sendAuthenticatedCommand(cmd: Int, bleKeyHex: String, masterKeyHex: String, masterRandomHex: String): Boolean {
        val writeChar = getWriteCharacteristic() ?: return false
        val notifyChar = getNotifyCharacteristic() ?: return false
        try {
            val keyBytes = hexToBytes(masterKeyHex)
            val randomBytes = hexToBytes(masterRandomHex)
            val authPayload = buildAuthPayload(cmd, keyBytes, randomBytes)

            setIndicationCallback(notifyChar).with { _, data ->
                _lastNotification = data.getValue()
            }

            suspendCancellableCoroutine<Unit> { cont ->
                enableIndications(notifyChar)
                    .done { if (cont.isActive) cont.resume(Unit) }
                    .fail { _, _ -> if (cont.isActive) cont.resume(Unit) }
                    .enqueue()
            }

            LogRepository.append("BLE", "写入 cmd=0x${cmd.toString(16)} len=${authPayload.size}")
            val writeResult = suspendCancellableCoroutine<Boolean> { cont ->
                writeCharacteristic(writeChar, authPayload).split()
                    .done { if (cont.isActive) cont.resume(true) }
                    .fail { _, status ->
                        if (cont.isActive) cont.resume(false)
                        LogRepository.append("BLE", "写入失败 cmd=0x${cmd.toString(16)} status=$status")
                    }
                    .enqueue()
            }

            if (!writeResult) {
                return false
            }
            LogRepository.append("BLE", "写入成功, 等待应答...")

            val notifData = withTimeoutOrNull(3000L) {
                while (_lastNotification == null) {
                    kotlinx.coroutines.delay(100)
                }
                _lastNotification
            }

            _lastNotification = null

            if (notifData != null && notifData.isNotEmpty()) {
                val valid = validateResponse(notifData)
                LogRepository.append("BLE", "应答 valid=$valid len=${notifData.size} hex=${notifData.joinToString("") { "%02x".format(it) }}")
                return valid
            }
            LogRepository.append("BLE", "写入成功(无应答) cmd=0x${cmd.toString(16)}")
            return true
        } catch (e: Exception) {
            LogRepository.append("BLE", "异常 cmd=0x${cmd.toString(16)}: ${e.message}")
            return false
        }
    }

    @Volatile
    private var _lastNotification: ByteArray? = null

    private fun buildAuthPayload(cmd: Int, keyBytes: ByteArray, randomBytes: ByteArray): ByteArray {
        val payload = mutableListOf<Byte>()

        payload.add(cmd.toByte())
        payload.addAll(randomBytes.toList())
        payload.addAll(keyBytes.toList())

        appendXorChecksum(payload)

        return payload.toByteArray()
    }

    private fun appendXorChecksum(bytes: MutableList<Byte>) {
        var xor: Byte = 0
        for (b in bytes) xor = (xor.toInt() xor b.toInt()).toByte()
        bytes.add(xor)
    }

    private fun validateResponse(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val payload = data.sliceArray(0 until data.size - 1)
        val checksum = data.last()
        var xor: Byte = 0
        for (b in payload) xor = (xor.toInt() xor b.toInt()).toByte()
        return xor == checksum
    }

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
