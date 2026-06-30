package com.wuling.keyless.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wuling_settings")

class KeyStorage(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("sgmw_access_token")
        private val KEY_CLIENT_ID = stringPreferencesKey("sgmw_client_id")
        private val KEY_CLIENT_SECRET = stringPreferencesKey("sgmw_client_secret")
        private val KEY_MAC = stringPreferencesKey("ble_mac")
        private val KEY_KEY = stringPreferencesKey("ble_key")
        private val KEY_MASTER_KEY = stringPreferencesKey("ble_master_key")
        private val KEY_MASTER_RANDOM = stringPreferencesKey("ble_master_random")
        private val KEY_VIN = stringPreferencesKey("vehicle_vin")
        private val KEY_CAR_NAME = stringPreferencesKey("car_name")
        private val KEY_SETUP_DONE = booleanPreferencesKey("setup_done")
        private val KEY_MODE = stringPreferencesKey("setup_mode")
        private val KEY_AUTO_LOCK = booleanPreferencesKey("auto_lock")
        private val KEY_AUTO_UNLOCK = booleanPreferencesKey("auto_unlock")
        private val KEY_UNLOCK_RSSI = intPreferencesKey("unlock_rssi")
        private val KEY_LOCK_RSSI = intPreferencesKey("lock_rssi")
    }

    suspend fun saveSgmwCredentials(accessToken: String, clientId: String, clientSecret: String) {
        context.dataStore.edit {
            it[KEY_TOKEN] = accessToken
            it[KEY_CLIENT_ID] = clientId
            it[KEY_CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun getToken(): String? = context.dataStore.data.map { it[KEY_TOKEN] }.first()
    suspend fun getClientId(): String? = context.dataStore.data.map { it[KEY_CLIENT_ID] }.first()
    suspend fun getClientSecret(): String? = context.dataStore.data.map { it[KEY_CLIENT_SECRET] }.first()

    suspend fun hasSgmwCredentials(): Boolean {
        val t = getToken()
        val id = getClientId()
        val s = getClientSecret()
        return !t.isNullOrEmpty() && !id.isNullOrEmpty() && !s.isNullOrEmpty()
    }

    suspend fun saveBleConfig(mac: String, key: String, vin: String? = null, masterKey: String? = null, masterRandom: String? = null) {
        context.dataStore.edit {
            it[KEY_MAC] = mac.uppercase().replace(":", "").replace("-", "")
            it[KEY_KEY] = key.replace(" ", "").uppercase()
            if (vin != null) it[KEY_VIN] = vin.uppercase()
            if (masterKey != null) it[KEY_MASTER_KEY] = masterKey.replace(" ", "").uppercase()
            if (masterRandom != null) it[KEY_MASTER_RANDOM] = masterRandom.replace(" ", "").uppercase()
        }
    }

    suspend fun getMac(): String? = context.dataStore.data.map { it[KEY_MAC] }.first()
    suspend fun getKey(): String? = context.dataStore.data.map { it[KEY_KEY] }.first()
    suspend fun getMasterKey(): String? = context.dataStore.data.map { it[KEY_MASTER_KEY] }.first()
    suspend fun getMasterRandom(): String? = context.dataStore.data.map { it[KEY_MASTER_RANDOM] }.first()
    suspend fun getVin(): String? = context.dataStore.data.map { it[KEY_VIN] }.first()

    suspend fun saveCarInfo(vin: String, carName: String) {
        context.dataStore.edit {
            it[KEY_VIN] = vin
            it[KEY_CAR_NAME] = carName
        }
    }

    suspend fun getCarName(): String? = context.dataStore.data.map { it[KEY_CAR_NAME] }.first()

    suspend fun isSetupDone(): Boolean = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }.first()

    suspend fun setSetupDone(done: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_DONE] = done }
    }

    suspend fun setSetupMode(mode: String) {
        context.dataStore.edit { it[KEY_MODE] = mode }
    }

    suspend fun getSetupMode(): String? = context.dataStore.data.map { it[KEY_MODE] }.first()

    suspend fun isAutoLockEnabled(): Boolean = context.dataStore.data.map { it[KEY_AUTO_LOCK] ?: true }.first()
    suspend fun isAutoUnlockEnabled(): Boolean = context.dataStore.data.map { it[KEY_AUTO_UNLOCK] ?: true }.first()

    suspend fun setAutoLock(v: Boolean) { context.dataStore.edit { it[KEY_AUTO_LOCK] = v } }
    suspend fun setAutoUnlock(v: Boolean) { context.dataStore.edit { it[KEY_AUTO_UNLOCK] = v } }

    suspend fun saveRssiThresholds(unlockRssi: Int, lockRssi: Int) {
        context.dataStore.edit {
            it[KEY_UNLOCK_RSSI] = unlockRssi
            it[KEY_LOCK_RSSI] = lockRssi
        }
    }

    suspend fun getRssiThresholds(): Pair<Int, Int>? {
        val data = context.dataStore.data
        val unlock = data.map { it[KEY_UNLOCK_RSSI] }.first()
        val lock = data.map { it[KEY_LOCK_RSSI] }.first()
        return if (unlock != null && lock != null) Pair(unlock, lock) else null
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        com.wuling.keyless.service.LogRepository.clear()
    }
}
