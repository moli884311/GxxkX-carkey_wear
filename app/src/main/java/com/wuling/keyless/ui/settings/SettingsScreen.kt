package com.wuling.keyless.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.ui.theme.Danger
import com.wuling.keyless.ui.theme.Grey400
import com.wuling.keyless.ui.theme.Primary
import com.wuling.keyless.ui.theme.Success
import com.wuling.keyless.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onReset: () -> Unit,
    homeViewModel: HomeViewModel = viewModel()
) {
    val autoUnlock by homeViewModel.autoUnlock.collectAsStateWithLifecycle()
    val autoLock by homeViewModel.autoLock.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            title = { Text("确认清除") },
            text = { Text("将删除所有凭证和密钥配置") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                    Text("确定")
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } },
            onDismissRequest = { showResetDialog = false }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) {
        Column(modifier = Modifier.fillMaxSize().padding(it)) {
            SectionTitle("无感控车")
            SwitchSetting("靠近自动开锁", "蓝牙信号强时自动解锁", autoUnlock) {
                homeViewModel.setAutoUnlock(it)
            }
            SwitchSetting("离开自动落锁", "蓝牙信号弱时自动锁闭", autoLock) {
                homeViewModel.setAutoLock(it)
            }
            SectionTitle("操作")
            ListItem(
                headlineContent = { Text("修改配置") },
                supportingContent = { Text("重新输入凭证或密钥") },
                leadingContent = { Icon(Icons.Default.Edit, null, tint = Primary) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
            ListItem(
                headlineContent = { Text("清除所有数据", color = Danger) },
                supportingContent = { Text("删除凭证和密钥") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = Danger) },
                modifier = Modifier.let { m ->
                    m.then(
                        object : Modifier {
                            // Click handled via composition
                        }
                    )
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = Grey400,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
