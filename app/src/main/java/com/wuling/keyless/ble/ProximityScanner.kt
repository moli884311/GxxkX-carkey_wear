package com.wuling.keyless.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.wuling.keyless.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ProximityScanner(private val context: Context, private val targetMac: String) {

    data class ProximityState(val rssi: Int, val label: String, val isNear: Boolean, val isFar: Boolean)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    fun isBluetoothReady(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<ProximityState> = callbackFlow {
        val adapter = bluetoothAdapter ?: run {
            close(Exception("蓝牙不可用"))
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            close(Exception("BLE扫描器不可用"))
            return@callbackFlow
        }

        val normalizedMac = targetMac.uppercase().replace(":", "").replace("-", "")
        val formattedMac = if (normalizedMac.length == 12) normalizedMac.chunked(2).joinToString(":") else normalizedMac

        val filters = if (normalizedMac.length == 12) {
            listOf(ScanFilter.Builder().setDeviceAddress(formattedMac).build())
        } else {
            listOf(ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(Constants.BLE_SERVICE_UUID))
                .build())
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rssi = result.rssi
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

            override fun onScanFailed(errorCode: Int) {
                // silently retry on next cycle
            }
        }

        scanner.startScan(filters, settings, scanCallback)

        awaitClose {
            try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
        }
    }

    fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDevice(): BluetoothDevice? {
        val normalizedMac = targetMac.uppercase().replace(":", "").replace("-", "")
        if (normalizedMac.length != 12) return null
        val formattedMac = normalizedMac.chunked(2).joinToString(":")
        return bluetoothAdapter?.getRemoteDevice(formattedMac)
    }
}
