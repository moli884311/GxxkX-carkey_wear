package com.wuling.keyless.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuling.keyless.Constants
import com.wuling.keyless.storage.KeyStorage
import com.wuling.keyless.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onReset: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { KeyStorage(context) }

    var showResetDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf<EditField?>(null) }
    var showSavedHint by remember { mutableStateOf(false) }

    var unlockRssi by remember { mutableStateOf("${Constants.DEFAULT_UNLOCK_RSSI}") }
    var lockRssi by remember { mutableStateOf("${Constants.DEFAULT_LOCK_RSSI}") }
    var cooldownSec by remember { mutableStateOf("${Constants.DEFAULT_COOLDOWN_SEC}") }
    var scanInterval by remember { mutableStateOf("${Constants.DEFAULT_RSSI_MONITOR_INTERVAL}") }
    var connectTimeout by remember { mutableStateOf("${Constants.DEFAULT_CONNECT_TIMEOUT}") }
    var reconnectInterval by remember { mutableStateOf("${Constants.DEFAULT_RECONNECT_INTERVAL}") }
    var authTimeout by remember { mutableStateOf("${Constants.DEFAULT_AUTH_TIMEOUT}") }
    var themeMode by remember { mutableStateOf("跟随系统") }

    var antiSleepBlind by remember { mutableStateOf(true) }
    var antiSleepRssi by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val stored = storage.getRssiThresholds()
        unlockRssi = stored?.first ?: "${Constants.DEFAULT_UNLOCK_RSSI}"
        lockRssi = stored?.second ?: "${Constants.DEFAULT_LOCK_RSSI}"
    }

    if (showResetDialog) {
        AlertDialog(
            title = { Text("确认清除") },
            text = { Text("将删除所有凭证和密钥配置") },
            confirmButton = {
                TextButton(
                    onClick = { showResetDialog = false; onReset() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            },
            onDismissRequest = { showResetDialog = false }
        )
    }

    editDialog?.let { field ->
        EditValueDialog(
            field = field,
            onDismiss = { editDialog = null },
            onSave = { newValue ->
                when (field.key) {
                    "unlock_rssi" -> unlockRssi = newValue
                    "lock_rssi" -> lockRssi = newValue
                    "cooldown" -> cooldownSec = newValue
                    "scan_interval" -> scanInterval = newValue
                    "connect_timeout" -> connectTimeout = newValue
                    "reconnect_interval" -> reconnectInterval = newValue
                    "auth_timeout" -> authTimeout = newValue
                }
                editDialog = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            storage.saveRssiThresholds(unlockRssi.toIntOrNull() ?: Constants.DEFAULT_UNLOCK_RSSI,
                                lockRssi.toIntOrNull() ?: Constants.DEFAULT_LOCK_RSSI)
                            showSavedHint = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (showSavedHint) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("已保存", fontSize = 15.sp)
                    } else {
                        Text("保存设置", fontSize = 15.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            // Distance and Control
            SettingSection(title = "距离与控制")
            SettingsCard {
                EditableRow(
                    label = "开锁 RSSI 阈值",
                    value = "$unlockRssi dBm",
                    helper = "推荐 -65 到 -75",
                    onClick = { editDialog = EditField("unlock_rssi", "开锁 RSSI 阈值", unlockRssi, "dBm", KeyboardType.Number) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditableRow(
                    label = "落锁 RSSI 阈值",
                    value = "$lockRssi dBm",
                    helper = "推荐 -80 到 -90",
                    onClick = { editDialog = EditField("lock_rssi", "落锁 RSSI 阈值", lockRssi, "dBm", KeyboardType.Number) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditableRow(
                    label = "控制指令冷却期",
                    value = "${cooldownSec} s",
                    helper = "推荐 5-15 秒",
                    onClick = { editDialog = EditField("cooldown", "控制指令冷却期", cooldownSec, "s", KeyboardType.Decimal) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Bluetooth Connection
            SettingSection(title = "蓝牙连接")
            SettingsCard {
                EditableRow(
                    label = "扫描间隔",
                    value = "${scanInterval} s",
                    helper = "推荐 1-3 秒",
                    onClick = { editDialog = EditField("scan_interval", "扫描间隔", scanInterval, "s", KeyboardType.Decimal) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditableRow(
                    label = "连接超时",
                    value = "${connectTimeout} s",
                    helper = "推荐 5-15 秒",
                    onClick = { editDialog = EditField("connect_timeout", "连接超时", connectTimeout, "s", KeyboardType.Decimal) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditableRow(
                    label = "断开重连间隔",
                    value = "${reconnectInterval} s",
                    helper = "推荐 1-5 秒",
                    onClick = { editDialog = EditField("reconnect_interval", "断开重连间隔", reconnectInterval, "s", KeyboardType.Decimal) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditableRow(
                    label = "鉴权阶段超时",
                    value = "${authTimeout} s",
                    helper = "推荐 2-5 秒",
                    onClick = { editDialog = EditField("auth_timeout", "鉴权阶段超时", authTimeout, "s", KeyboardType.Decimal) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Advanced & Keep-alive
            SettingSection(title = "高级与保活")
            SettingsCard {
                EditableRow(
                    label = "RSSI 监测间隔",
                    value = "${scanInterval} s",
                    helper = "推荐 0.5-2 秒",
                    onClick = { editDialog = EditField("scan_interval", "RSSI 监测间隔", scanInterval, "s", KeyboardType.Decimal) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow(
                    label = "防休眠（盲连阶段）",
                    description = "连接尝试期间保持 CPU 唤醒",
                    checked = antiSleepBlind,
                    onCheckedChange = { antiSleepBlind = it }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow(
                    label = "防休眠（RSSI监测阶段）",
                    description = "距离监测期间保持 CPU 唤醒",
                    checked = antiSleepRssi,
                    onCheckedChange = { antiSleepRssi = it }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Appearance
            SettingSection(title = "外观")
            SettingsCard {
                NavigableRow(
                    icon = Icons.Default.Palette,
                    label = "主题模式",
                    value = themeMode
                )
            }

            Spacer(Modifier.height(16.dp))

            // Danger zone
            SettingSection(title = "操作")
            SettingsCard {
                ActionRow(
                    icon = Icons.Default.DeleteForever,
                    label = "清除所有数据",
                    description = "删除凭证和密钥",
                    contentColor = Danger,
                    onClick = { showResetDialog = true }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

private data class EditField(
    val key: String,
    val label: String,
    val currentValue: String,
    val unit: String,
    val keyboardType: KeyboardType
)

@Composable
private fun EditValueDialog(field: EditField, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(field.currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = field.keyboardType),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(field.unit, fontSize = 16.sp, color = Grey400)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("保存", color = Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingSection(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Grey400,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun EditableRow(
    label: String,
    value: String,
    helper: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.W500)
            Text(helper, fontSize = 12.sp, color = Grey400, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.W500, color = Primary)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Primary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.W500)
            Text(description, fontSize = 12.sp, color = Grey400, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Primary
            )
        )
    }
}

@Composable
private fun NavigableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Grey400)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Grey400, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    contentColor: androidx.compose.ui.graphics.Color = Primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.W500, color = contentColor)
            Text(description, fontSize = 12.sp, color = Grey400)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Grey400, modifier = Modifier.size(20.dp))
    }
}
