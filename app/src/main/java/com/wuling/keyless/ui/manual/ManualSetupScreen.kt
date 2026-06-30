package com.wuling.keyless.ui.manual

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.wuling.keyless.api.BleConfigImporter
import com.wuling.keyless.api.WulingApi
import com.wuling.keyless.storage.KeyStorage
import com.wuling.keyless.ui.theme.Primary
import com.wuling.keyless.ui.theme.Danger
import com.wuling.keyless.ui.theme.Success
import com.wuling.keyless.ui.theme.Warning
import com.wuling.keyless.viewmodel.LoginViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSetupScreen(
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var mac by remember { mutableStateOf("") }
    var masterKey by remember { mutableStateOf("") }
    var masterRandom by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    var cloudSyncing by remember { mutableStateOf(false) }
    var cloudSyncError by remember { mutableStateOf<String?>(null) }

    fun doCloudSync() {
        cloudSyncing = true
        cloudSyncError = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val storage = KeyStorage(context)
                val token = withContext(Dispatchers.IO) { storage.getToken() }
                val cid = withContext(Dispatchers.IO) { storage.getClientId() }
                val csecret = withContext(Dispatchers.IO) { storage.getClientSecret() }

                if (token.isNullOrEmpty() || cid.isNullOrEmpty() || csecret.isNullOrEmpty()) {
                    cloudSyncError = "请先在「账号登录」或「凭证模式」中配置 Sgmw API 凭证"
                    cloudSyncing = false
                    return@launch
                }

                val api = WulingApi(token, cid, csecret)
                val result = withContext(Dispatchers.IO) { api.queryBleKeyConfig() }
                if (!result.success) {
                    cloudSyncError = result.error ?: "云端获取失败"
                    cloudSyncing = false
                    return@launch
                }

                if (result.address.isNotBlank()) mac = result.address
                if (result.masterKey.isNotBlank()) masterKey = result.masterKey
                if (result.masterRandom.isNotBlank()) masterRandom = result.masterRandom
                if (result.vin.isNotBlank()) vin = result.vin

                Toast.makeText(context, "云端 BLE 配置获取成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                cloudSyncError = "获取失败: ${e.message}"
            } finally {
                cloudSyncing = false
            }
        }
    }

    LaunchedEffect(result) {
        if (result == "ble_ok") onSuccess()
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importError = null },
            title = { Text("导入加密配置") },
            text = {
                Column {
                    Text("粘贴 BLE 加密配置 JSON 并输入导出密码", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importJson,
                        onValueChange = { importJson = it; importError = null },
                        label = { Text("JSON 配置内容") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = importPassword,
                            onValueChange = { importPassword = it; importError = null },
                            label = { Text("导出密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (importError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(importError!!, color = Danger, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val config = BleConfigImporter.decrypt(importJson, importPassword)
                            mac = config.address
                            masterKey = config.masterKey
                            masterRandom = config.masterRandom
                            vin = config.vin
                            showImportDialog = false
                            Toast.makeText(context, "配置导入成功: ${config.name}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            importError = "解密失败: ${e.message}"
                        }
                    },
                    enabled = importJson.isNotBlank() && importPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importError = null }) { Text("取消") }
            }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("手动输入配置") }) }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Import button
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
            ) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("导入加密 BLE 配置", fontSize = 14.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { doCloudSync() },
                enabled = !cloudSyncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Success)
            ) {
                if (cloudSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (cloudSyncing) "获取中..." else "从云端获取 BLE 配置", fontSize = 14.sp)
            }

            if (cloudSyncError != null) {
                Text(cloudSyncError!!, color = Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                color = Warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "需要完整的 BLE 鉴权参数（导出包解密后获得）。\nmasterKey 和 masterRandom 为 16 字节 HEX。",
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = mac, onValueChange = { mac = it.uppercase().take(17) },
                label = { Text("BLE MAC 地址") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                leadingIcon = { Icon(Icons.Default.Bluetooth, null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = masterKey, onValueChange = { masterKey = it.uppercase().take(32) },
                label = { Text("masterKey (16字节 HEX)") },
                placeholder = { Text("32位 HEX") },
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false, maxLines = 2
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = masterRandom, onValueChange = { masterRandom = it.uppercase().take(32) },
                label = { Text("masterRandom (16字节 HEX)") },
                placeholder = { Text("32位 HEX") },
                leadingIcon = { Icon(Icons.Default.LockReset, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false, maxLines = 2
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = vin, onValueChange = { vin = it.uppercase().take(17) },
                label = { Text("VIN 车辆识别码 (可选)") },
                placeholder = { Text("17位 VIN 码") },
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { viewModel.saveBleManual(mac, masterKey, vin.ifEmpty { null }, masterRandom) },
                enabled = mac.length >= 12 && masterKey.length >= 32 && masterRandom.length >= 32,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("保存配置", fontSize = 15.sp)
            }
        }
    }
}
