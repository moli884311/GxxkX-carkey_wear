package com.wuling.keyless.api

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BleConfig(
    val name: String,
    val address: String,
    val bleKey: String,
    val masterKey: String,
    val masterRandom: String,
    val vin: String,
    val keyId: String,
    val userId: String,
    val serviceUuid: String,
    val writeUuid: String,
    val notifyUuid: String
)

object BleConfigImporter {

    fun decrypt(jsonStr: String, password: String): BleConfig {
        val root = JSONObject(jsonStr)
        val kdf = root.getJSONObject("kdf")
        val cipher = root.getJSONObject("cipher")

        val salt = Base64.decode(kdf.getString("saltBase64"), Base64.DEFAULT)
        val iterations = kdf.getInt("iterations")
        val iv = Base64.decode(cipher.getString("ivBase64"), Base64.DEFAULT)
        val tag = Base64.decode(cipher.getString("tagBase64"), Base64.DEFAULT)
        val ct = Base64.decode(cipher.getString("ciphertextBase64"), Base64.DEFAULT)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKey = keyFactory.generateSecret(keySpec).encoded

        val gcmSpec = GCMParameterSpec(128, iv)
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), gcmSpec)

        val combined = ct + tag
        val plaintext = aesCipher.doFinal(combined)
        val payload = JSONObject(String(plaintext))

        return BleConfig(
            name = payload.optString("name", ""),
            address = payload.optString("address", ""),
            bleKey = payload.optString("bleKey", ""),
            masterKey = payload.optString("masterKey", ""),
            masterRandom = payload.optString("masterRandom", ""),
            vin = payload.optString("vin", ""),
            keyId = payload.optString("keyId", ""),
            userId = payload.optString("userId", ""),
            serviceUuid = payload.optString("serviceUuid", ""),
            writeUuid = payload.optString("writeUuid", ""),
            notifyUuid = payload.optString("notifyUuid", "")
        )
    }
}
