package com.wuling.keyless.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.wuling.keyless.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.DataReceived
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BleConnector : BleManager() {

    suspend fun sendLock(keyHex: String): Boolean = sendCommand(Constants.CMD_LOCK, keyHex)
    suspend fun sendUnlock(keyHex: String): Boolean = sendCommand(Constants.CMD_UNLOCK, keyHex)

    private suspend fun sendCommand(cmd: Int, keyHex: String): Boolean {
        val char = getCharacteristic() ?: return false
        try {
            val keyBytes = hexToBytes(keyHex)
            val data = mutableListOf(cmd.toByte()).apply { addAll(keyBytes.toList()) }
            appendChecksum(data)

            val result = writeAndWait(char, data.toByteArray())
            val response = readAndWait(char)
            return response != null && validateChecksum(response)
        } catch (_: Exception) {
            return false
        }
    }

    private fun getCharacteristic(): BluetoothGattCharacteristic? {
        val services = bluetoothGatt?.services ?: return null
        for (s in services) {
            if (s.uuid.toString().uppercase() == Constants.BLE_SERVICE_UUID.uppercase()) {
                return s.getCharacteristic(java.util.UUID.fromString(Constants.BLE_CMD_CHAR_UUID))
            }
        }
        return null
    }

    private suspend fun writeAndWait(char: BluetoothGattCharacteristic, data: ByteArray): ByteArray? =
        suspendCancellableCoroutine { cont ->
            writeCharacteristic(char, data).with { _, _ ->
                if (cont.isActive) cont.resume(Unit)
            }.enqueue()
        }.let {
            readAndWait(char)
        }

    private suspend fun readAndWait(char: BluetoothGattCharacteristic): ByteArray? =
        suspendCancellableCoroutine { cont ->
            readCharacteristic(char).with { _, data: DataReceived ->
                if (cont.isActive) cont.resume(data.value)
            }.fail { _, status ->
                if (cont.isActive) cont.resumeWithException(RuntimeException("BLE read failed: $status"))
            }.enqueue()
        }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    private fun appendChecksum(bytes: MutableList<Byte>) {
        var sum = 0
        for (b in bytes) sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        bytes.add(sum.toByte())
    }

    private fun validateChecksum(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val payload = data.sliceArray(0 until data.size - 1)
        val checksum = data.last().toInt() and 0xFF
        var sum = 0
        for (b in payload) sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        return sum == checksum
    }

    override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: android.bluetooth.BluetoothGatt): Boolean = true
        override fun onServicesInvalidated() {}
    }
}
