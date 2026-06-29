package com.wuling.keyless.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuling.keyless.api.WulingApi
import com.wuling.keyless.service.DoorState
import com.wuling.keyless.service.ProximityService
import com.wuling.keyless.storage.KeyStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            _autoUnlock.value = storage.isAutoUnlockEnabled()
            _autoLock.value = storage.isAutoLockEnabled()
            service.logs.collect { msg ->
                val updated = _logs.value.toMutableList()
                updated.add(msg)
                if (updated.size > 50) updated.removeAt(0)
                _logs.value = updated
            }
        }
        viewModelScope.launch { service.start() }
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

    fun saveBleManual(mac: String, key: String, vin: String?) {
        viewModelScope.launch {
            storage.saveBleConfig(mac, key, vin)
            storage.setSetupMode("ble")
            storage.setSetupDone(true)
            _result.value = "ble_ok"
        }
    }

    fun clearError() { _error.value = null }
}
