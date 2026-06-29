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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.ui.theme.Success
import com.wuling.keyless.ui.theme.Warning
import com.wuling.keyless.viewmodel.LoginViewModel

@Composable
fun ManualSetupScreen(
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val result by viewModel.result.collectAsStateWithLifecycle()

    var mac by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }

    LaunchedEffect(result) {
        if (result == "ble_ok") onSuccess()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("手动输入配置") }) }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("此模式适用于安卓手表等离线设备，配置完成后全程蓝牙工作无需网络", fontSize = 13.sp)
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
                value = key, onValueChange = { key = it },
                label = { Text("BLE 密钥 (HEX)") },
                placeholder = { Text("输入车辆的 BLE 通信密钥") },
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false, maxLines = 3
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
                onClick = { viewModel.saveBleManual(mac, key, vin.ifEmpty { null }) },
                enabled = mac.length >= 12 && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("保存配置", fontSize = 15.sp)
            }
        }
    }
}
