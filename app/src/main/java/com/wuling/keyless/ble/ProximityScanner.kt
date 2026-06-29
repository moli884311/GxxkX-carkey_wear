package com.wuling.keyless.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wuling.keyless.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import no.nordicsemi.android.ble.BleManager

class ProximityScanner(private val context: Context, private val targetMac: String) {

    data class ProximityState(val rssi: Int, val label: String, val isNear: Boolean, val isFar: Boolean)

    private var lastRssi: Int = -100

    fun isBluetoothReady(): Boolean = true

    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<ProximityState> = callbackFlow {
        while (isActive) {
            if (hasBlePermission()) {
                val device = BleManager.getBondedDevices()
                    .firstOrNull { it.address.replace(":", "").uppercase() == targetMac }

                if (device != null) {
                    device.connectGatt(context, false, object : android.bluetooth.BluetoothGattCallback() {
                        override fun onReadRemoteRssi(gatt: android.bluetooth.BluetoothGatt, rssi: Int, status: Int) {
                            lastRssi = rssi
                            val isNear = rssi >= Constants.RSSI_NEAR_THRESHOLD
                            val isFar = rssi <= Constants.RSSI_FAR_THRESHOLD
                            val label = when {
                                rssi >= -50 -> "极近"
                                rssi >= Constants.RSSI_NEAR_THRESHOLD -> "靠近"
                                rssi <= Constants.RSSI_FAR_THRESHOLD -> "远离"
                                else -> "中等距离"
                            }
                            trySend(ProximityState(rssi, label, isNear, isFar))
                        }
                    }, android.bluetooth.BluetoothDevice.TRANSPORT_LE).use { gatt ->
                        delay(500)
                        gatt.readRemoteRssi()
                        delay(Constants.SCAN_INTERVAL_MS)
                        gatt.close()
                    }
                }
            }
            delay(Constants.SCAN_INTERVAL_MS)
        }
        awaitClose()
    }

    fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
