package com.wuling.keyless.api

import com.wuling.keyless.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WulingApi(
    private val accessToken: String,
    private val clientId: String,
    private val clientSecret: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private val loginClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        suspend fun loginWithPassword(phone: String, password: String): LoginResult = withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("phone", phone)
                    put("password", password)
                    put("type", "password")
                }
                val request = Request.Builder()
                    .url("${Constants.API_BASE}/user/login")
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "okhttp/4.9.0")
                    .addHeader("channel", "linglingbang")
                    .addHeader("platformNo", "Android")
                    .addHeader("appVersionCode", Constants.APP_VERSION)
                    .addHeader("deviceModel", "Android")
                    .addHeader("deviceBrand", "Android")
                    .addHeader("deviceType", "Android")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = loginClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext LoginResult(false, error = "响应为空")

                if (!response.isSuccessful) {
                    return@withContext LoginResult(false, error = "HTTP ${response.code}: ${body.take(200)}")
                }

                val json = try { JSONObject(body) } catch (_: Exception) {
                    return@withContext LoginResult(false, error = "解析失败: ${body.take(200)}")
                }
                val code = json.optString("code", "")
                if (code != "200") {
                    val msg = json.optString("msg", "").ifEmpty { "无错误信息" }
                    return@withContext LoginResult(false, error = "code=$code msg=$msg")
                }

                val data = json.optJSONObject("data") ?: return@withContext LoginResult(false, error = "数据为空")
                val token = data.optString("sgmwaccesstoken", data.optString("accessToken", ""))
                val cid = data.optString("sgmwclientid", data.optString("clientId", ""))
                val csecret = data.optString("sgmwclientsecret", data.optString("clientSecret", ""))

                LoginResult(
                    success = true,
                    accessToken = token,
                    clientId = cid,
                    clientSecret = csecret
                )
            } catch (e: Exception) {
                LoginResult(false, error = "网络异常: ${e.message}")
            }
        }
    }

    private fun generateSignature(): SgmwHeaders {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = (100000..999999).random().toString()

        val raw = "$accessToken$timestamp$nonce$clientId$clientSecret${Constants.APP_CODE}${Constants.APP_VERSION}${Constants.SYSTEM}${Constants.SYSTEM_VERSION}"
        val signature = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .uppercase()

        return SgmwHeaders(
            token = accessToken,
            timestamp = timestamp,
            nonce = nonce,
            clientId = clientId,
            clientSecret = clientSecret,
            signature = signature,
            appCode = Constants.APP_CODE,
            appVersion = Constants.APP_VERSION,
            system = Constants.SYSTEM,
            systemVersion = Constants.SYSTEM_VERSION
        )
    }

    suspend fun getCarStatus(): VehicleStatusResult = withContext(Dispatchers.IO) {
        try {
            val headers = generateSignature()
            val request = Request.Builder()
                .url("${Constants.API_BASE}/userCarRelation/queryDefaultCarStatus")
                .applySgmwHeaders(headers)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext VehicleStatusResult(false, error = "响应为空")

            val json = JSONObject(body)
            val code = json.optString("code", "")
            if (code != "200") {
                return@withContext VehicleStatusResult(false, error = json.optString("msg", "API 请求失败"))
            }

            val data = json.optJSONObject("data") ?: return@withContext VehicleStatusResult(false, error = "数据为空")
            val carInfo = data.optJSONObject("carInfo")
            val carStatus = data.optJSONObject("carStatus")

            VehicleStatusResult(
                success = true,
                vin = carInfo?.optString("vin", "") ?: "",
                carName = carInfo?.optString("carName", "") ?: "",
                batterySoc = carStatus?.optString("batterySoc", "") ?: "",
                mileage = carStatus?.optString("mileage", "") ?: "",
                doorLockStatus = carStatus?.optString("doorLockStatus", "") ?: "",
                acStatus = carStatus?.optString("acStatus", "") ?: ""
            )
        } catch (e: Exception) {
            VehicleStatusResult(false, error = "网络异常: ${e.message}")
        }
    }

    suspend fun queryBleKeyConfig(): BleKeyResult = withContext(Dispatchers.IO) {
        try {
            val headers = generateSignature()
            val request = Request.Builder()
                .url("${Constants.API_BASE}/carKey/queryDefaultCarKey")
                .applySgmwHeaders(headers)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext BleKeyResult(false, error = "响应为空")

            val json = JSONObject(body)
            val code = json.optString("code", "")
            if (code != "200") {
                return@withContext BleKeyResult(false, error = json.optString("msg", "BLE 密钥查询失败"))
            }

            val data = json.optJSONObject("data") ?: return@withContext BleKeyResult(false, error = "数据为空")
            BleKeyResult(
                success = true,
                address = data.optString("address", ""),
                bleKey = data.optString("bleKey", ""),
                masterKey = data.optString("masterKey", ""),
                masterRandom = data.optString("masterRandom", ""),
                vin = data.optString("vin", ""),
                keyId = data.optString("keyId", ""),
                serviceUuid = data.optString("serviceUuid", ""),
                writeUuid = data.optString("writeUuid", ""),
                notifyUuid = data.optString("notifyUuid", "")
            )
        } catch (e: Exception) {
            BleKeyResult(false, error = "网络异常: ${e.message}")
        }
    }

    suspend fun lockCar(): CommandResult = withContext(Dispatchers.IO) {
        sendCommand("lock")
    }

    suspend fun unlockCar(): CommandResult = withContext(Dispatchers.IO) {
        sendCommand("unlock")
    }

    suspend fun findCar(): CommandResult = withContext(Dispatchers.IO) {
        sendCommand("find")
    }

    suspend fun acControl(on: Boolean): CommandResult = withContext(Dispatchers.IO) {
        sendCommand(if (on) "acOn" else "acOff")
    }

    private suspend fun sendCommand(cmd: String): CommandResult = try {
            val headers = generateSignature()
            val jsonBody = JSONObject().apply { put("command", cmd) }
            val request = Request.Builder()
                .url("${Constants.API_BASE}/control/sendCommand")
                .applySgmwHeaders(headers)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return CommandResult(false, error = "响应为空")
            val json = JSONObject(body)
            val code = json.optString("code", "")

            if (code == "200") {
                CommandResult(true, "指令发送成功")
            } else {
                CommandResult(false, error = json.optString("msg", "指令发送失败"))
            }
        } catch (e: Exception) {
            CommandResult(false, error = "网络异常: ${e.message}")
        }

    data class SgmwHeaders(
        val token: String,
        val timestamp: String,
        val nonce: String,
        val clientId: String,
        val clientSecret: String,
        val signature: String,
        val appCode: String,
        val appVersion: String,
        val system: String,
        val systemVersion: String
    )

    private fun Request.Builder.applySgmwHeaders(h: SgmwHeaders): Request.Builder {
        addHeader("sgmwaccesstoken", h.token)
        addHeader("sgmwtimestamp", h.timestamp)
        addHeader("sgmwnonce", h.nonce)
        addHeader("sgmwclientid", h.clientId)
        addHeader("sgmwclientsecret", h.clientSecret)
        addHeader("sgmwsignature", h.signature)
        addHeader("sgmwappcode", h.appCode)
        addHeader("sgmwappversion", h.appVersion)
        addHeader("sgmwsystem", h.system)
        addHeader("sgmwsystemversion", h.systemVersion)
        addHeader("Content-Type", "application/json")
        return this
    }

    data class VehicleStatusResult(
        val success: Boolean,
        val vin: String = "",
        val carName: String = "",
        val batterySoc: String = "",
        val mileage: String = "",
        val doorLockStatus: String = "",
        val acStatus: String = "",
        val error: String? = null
    )

    data class CommandResult(
        val success: Boolean,
        val message: String = "",
        val error: String? = null
    )

    data class LoginResult(
        val success: Boolean,
        val accessToken: String = "",
        val clientId: String = "",
        val clientSecret: String = "",
        val error: String? = null
    )

    data class BleKeyResult(
        val success: Boolean,
        val address: String = "",
        val bleKey: String = "",
        val masterKey: String = "",
        val masterRandom: String = "",
        val vin: String = "",
        val keyId: String = "",
        val serviceUuid: String = "",
        val writeUuid: String = "",
        val notifyUuid: String = "",
        val error: String? = null
    )
}
