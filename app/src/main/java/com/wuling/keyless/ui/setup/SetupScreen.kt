package com.wuling.keyless.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuling.keyless.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onSgmw: () -> Unit, onBle: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("初始配置") }) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(72.dp), tint = Primary)
            Spacer(Modifier.height(24.dp))
            Text("选择配置方式", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("获取车辆凭证以启用无感控车和远程控制", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(48.dp))
            OptionCard(
                icon = Icons.Default.CloudDownload,
                title = "Sgmw API 凭证",
                subtitle = "从五菱官方App抓包获取 accessToken、clientId、clientSecret，支持远程控车",
                onClick = onSgmw
            )
            Spacer(Modifier.height(16.dp))
            OptionCard(
                icon = Icons.Default.EditNote,
                title = "手动 BLE 配置",
                subtitle = "直接输入车辆蓝牙 MAC 地址和密钥，全程蓝牙无需网络，手表/离线可用",
                onClick = onBle
            )
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Primary)
        }
    }
}
