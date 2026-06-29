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

    private suspend fun sendCommand(cmd: String): CommandResult {
        try {
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
}
