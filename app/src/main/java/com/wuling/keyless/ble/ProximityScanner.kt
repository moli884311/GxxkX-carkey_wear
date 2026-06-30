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
import com.wuling.keyless.service.LogRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ProximityScanner(private val context: Context, private val targetMac: String) {

    data class ProximityState(val rssi: Int, val label: String, val isNear: Boolean, val isFar: Boolean)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val normalizedTargetMac = targetMac.uppercase().replace(":", "").replace("-", "")

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

        val filters: List<ScanFilter>? = if (normalizedTargetMac.length == 12) {
            null
        } else {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(Constants.BLE_SERVICE_UUID))
                    .build()
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setLegacy(false)
            .setReportDelay(0)
            .build()

        var resultCount = 0

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                resultCount++
                val deviceMac = result.device.address.uppercase().replace(":", "")

                if (normalizedTargetMac.length == 12 && deviceMac != normalizedTargetMac) {
                    if (resultCount == 1) {
                        LogRepository.append("Scan", "扫描活跃, 首个设备 ${result.device.address} RSSI=${result.rssi}")
                    }
                    return
                }

                val rssi = result.rssi
                val isNear = rssi >= Constants.RSSI_NEAR_THRESHOLD
                val isFar = rssi <= Constants.RSSI_FAR_THRESHOLD
                val label = when {
                    rssi >= -50 -> "极近"
                    rssi >= Constants.RSSI_NEAR_THRESHOLD -> "靠近"
                    rssi <= Constants.RSSI_FAR_THRESHOLD -> "远离"
                    else -> "中等距离"
                }
                channel.trySend(ProximityState(rssi, label, isNear, isFar))
            }

            override fun onScanFailed(errorCode: Int) {
                LogRepository.append("Scan", "BLE扫描失败 errorCode=$errorCode")
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(0, it) }
            }
        }

        LogRepository.append("Scan", "开始扫描 mac=$normalizedTargetMac mode=BALANCED")

        scanner.startScan(filters, settings, scanCallback)

        awaitClose {
            try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
            LogRepository.append("Scan", "扫描已停止 (收到${resultCount}个结果)")
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
        if (normalizedTargetMac.length != 12) return null
        val formattedMac = normalizedTargetMac.chunked(2).joinToString(":")
        return bluetoothAdapter?.getRemoteDevice(formattedMac)
    }
}
