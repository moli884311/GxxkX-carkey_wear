package com.wuling.keyless

object Constants {
    const val API_BASE = "https://openapi.baojun.net/junApi/sgmw"

    const val APP_CODE = "SgmwApp"
    const val APP_VERSION = "1.0.0"
    const val SYSTEM = "Android"
    const val SYSTEM_VERSION = "14"

    const val BLE_SERVICE_UUID = "0000181a-0000-1000-8000-00805F9B34FB"
    const val BLE_WRITE_CHAR_UUID = "00002a6e-0000-1000-8000-00805F9B34FB"
    const val BLE_NOTIFY_CHAR_UUID = "00002a6f-0000-1000-8000-00805F9B34FB"
    const val BLE_CMD_CHAR_UUID = "00002a6e-0000-1000-8000-00805F9B34FB"

    const val CMD_LOCK = 0x01
    const val CMD_UNLOCK = 0x02

    const val RSSI_NEAR_THRESHOLD = -75
    const val RSSI_FAR_THRESHOLD = -85

    const val LOCK_DEBOUNCE_SEC = 10
    const val CONNECTION_TIMEOUT_SEC = 10

    const val SCAN_INTERVAL_MS = 2000L

    const val DEFAULT_UNLOCK_RSSI = -75
    const val DEFAULT_LOCK_RSSI = -85
    const val DEFAULT_COOLDOWN_SEC = 6.0f
    const val DEFAULT_CONNECT_RETRY_INTERVAL = 1.2f
    const val DEFAULT_CONNECT_TIMEOUT = 33.0f
    const val DEFAULT_RECONNECT_INTERVAL = 1.2f
    const val DEFAULT_AUTH_TIMEOUT = 2.5f
    const val DEFAULT_RSSI_MONITOR_INTERVAL = 0.5f

    const val MAX_RETRY_COUNT = 10
    const val RETRY_BACKOFF_BASE_MS = 2000L
    const val RETRY_BACKOFF_MAX_MS = 60000L
}
