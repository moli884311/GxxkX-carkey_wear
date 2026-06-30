package com.wuling.keyless.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuling.keyless.api.WulingApi
import com.wuling.keyless.service.ConnectionState
import com.wuling.keyless.service.DoorState
import com.wuling.keyless.service.ProximityService
import com.wuling.keyless.storage.KeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = KeyStorage(application)
    private val service = ProximityService(application)

    val status: StateFlow<ProximityService.Status> = service.status

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _autoUnlock = MutableStateFlow(true)
    val autoUnlock: StateFlow<Boolean> = _autoUnlock.asStateFlow()

    private val _autoLock = MutableStateFlow(true)
    val autoLock: StateFlow<Boolean> = _autoLock.asStateFlow()

    private val _smartKeyEnabled = MutableStateFlow(true)
    val smartKeyEnabled: StateFlow<Boolean> = _smartKeyEnabled.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _autoUnlock.value = storage.isAutoUnlockEnabled()
            _autoLock.value = storage.isAutoLockEnabled()
            service.logs.collect { msg ->
                val updated = _logs.value.toMutableList()
                updated.add(msg)
                if (updated.size > 100) updated.removeAt(0)
                _logs.value = updated
            }
        }
        viewModelScope.launch { service.start() }
    }

    fun setSmartKeyEnabled(v: Boolean) {
        viewModelScope.launch {
            _smartKeyEnabled.value = v
            if (v) {
                service.start()
            } else {
                service.stop()
            }
        }
    }

    fun setAutoUnlock(v: Boolean) {
        viewModelScope.launch {
            _autoUnlock.value = v
            storage.setAutoUnlock(v)
        }
    }

    fun setAutoLock(v: Boolean) {
        viewModelScope.launch {
            _autoLock.value = v
            storage.setAutoLock(v)
        }
    }

    fun manualLock() {
        viewModelScope.launch {
            _toastMessage.value = withContext(Dispatchers.IO) { service.manualLock() }
        }
    }

    fun manualUnlock() {
        viewModelScope.launch {
            _toastMessage.value = withContext(Dispatchers.IO) { service.manualUnlock() }
        }
    }

    fun manualPark() {
        viewModelScope.launch {
            _toastMessage.value = withContext(Dispatchers.IO) { service.manualPark() }
        }
    }

    fun clearToast() { _toastMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { service.stop() }
    }
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = KeyStorage(application)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun validateAndSave(token: String, clientId: String, clientSecret: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val api = WulingApi(token, clientId, clientSecret)
                val status = api.getCarStatus()
                if (status.success) {
                    storage.saveSgmwCredentials(token, clientId, clientSecret)
                    if (status.vin.isNotEmpty()) storage.saveCarInfo(status.vin, status.carName)
                    storage.setSetupMode("sgmw")
                    storage.setSetupDone(true)
                    _result.value = status.carName
                } else {
                    _error.value = status.error ?: "凭证验证失败"
                }
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loginWithPassword(phone: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val loginResult = withContext(Dispatchers.IO) {
                    WulingApi.loginWithPassword(phone, password)
                }
                if (!loginResult.success) {
                    _error.value = loginResult.error ?: "登录失败"
                    _loading.value = false
                    return@launch
                }

                val token = loginResult.accessToken
                val cid = loginResult.clientId
                val csecret = loginResult.clientSecret
                storage.saveSgmwCredentials(token, cid, csecret)

                val api = WulingApi(token, cid, csecret)

                val carStatus = withContext(Dispatchers.IO) { api.getCarStatus() }
                if (!carStatus.success) {
                    _error.value = "车辆信息获取失败: ${carStatus.error ?: "未知错误"}"
                    _loading.value = false
                    return@launch
                }

                if (carStatus.vin.isNotEmpty()) {
                    storage.saveCarInfo(carStatus.vin, carStatus.carName)
                }

                val bleKey = withContext(Dispatchers.IO) { api.queryBleKeyConfig() }
                if (bleKey.success) {
                    val mac = bleKey.address
                    val mk = bleKey.masterKey
                    val mr = bleKey.masterRandom
                    val vin = bleKey.vin.ifEmpty { carStatus.vin }
                    storage.saveBleConfig(
                        mac = mac,
                        key = bleKey.bleKey.ifEmpty { mk },
                        vin = vin,
                        masterKey = mk,
                        masterRandom = mr
                    )
                }

                storage.setSetupMode("sgmw")
                storage.setSetupDone(true)
                _result.value = carStatus.carName
            } catch (e: Exception) {
                _error.value = "网络异常: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveBleManual(mac: String, key: String, vin: String?, masterRandom: String? = null) {
        viewModelScope.launch {
            storage.saveBleConfig(mac, key, vin, masterKey = key, masterRandom = masterRandom)
            storage.setSetupMode("ble")
            storage.setSetupDone(true)
            _result.value = "ble_ok"
        }
    }

    fun clearError() { _error.value = null }
}
