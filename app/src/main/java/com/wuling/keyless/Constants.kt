package com.wuling.keyless

object Constants {
    const val API_BASE = "https://openapi.baojun.net/junApi/sgmw"

    const val APP_CODE = "SgmwApp"
    const val APP_VERSION = "1.0.0"
    const val SYSTEM = "Android"
    const val SYSTEM_VERSION = "14"

    const val BLE_SERVICE_UUID = "0000FFE0-0000-1000-8000-00805F9B34FB"
    const val BLE_CMD_CHAR_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB"

    const val CMD_LOCK = 0x01
    const val CMD_UNLOCK = 0x02

    const val RSSI_NEAR_THRESHOLD = -55
    const val RSSI_FAR_THRESHOLD = -75

    const val LOCK_DEBOUNCE_SEC = 10
    const val CONNECTION_TIMEOUT_SEC = 10

    const val SCAN_INTERVAL_MS = 2000L
}
