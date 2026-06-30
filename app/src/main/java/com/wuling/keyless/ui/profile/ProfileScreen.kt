package com.wuling.keyless.ui.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuling.keyless.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onReset: () -> Unit) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

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

    if (showLogoutDialog) {
        AlertDialog(
            title = { Text("确认退出") },
            text = { Text("退出登录将清除所有凭证和配置数据") },
            confirmButton = {
                TextButton(
                    onClick = { showLogoutDialog = false; onReset() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) { Text("确定退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            },
            onDismissRequest = { showLogoutDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Profile header card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "五菱无感控车",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "靠近开锁  离开落锁",
                        fontSize = 13.sp,
                        color = Grey400
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action items
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileActionItem(
                        icon = Icons.Default.Share,
                        title = "分享运行日志",
                        subtitle = "导出日志便于问题排查",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "五菱无感控车运行日志")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileActionItem(
                        icon = Icons.Default.Info,
                        title = "关于应用",
                        subtitle = "版本 v1.0.0",
                        onClick = {
                            Toast.makeText(context, "五菱无感控车 v1.0.0\n基于 BLE RSSI 无感控车", Toast.LENGTH_LONG).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileActionItem(
                        icon = Icons.Default.DeleteForever,
                        title = "清除所有数据",
                        subtitle = "删除凭证和密钥",
                        contentColor = Danger,
                        onClick = { showResetDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileActionItem(
                        icon = Icons.Default.Logout,
                        title = "退出登录",
                        subtitle = "清除数据并返回初始配置",
                        contentColor = Danger,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "v1.0.0",
                fontSize = 12.sp,
                color = Grey400.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    contentColor: androidx.compose.ui.graphics.Color = OnSurface,
    onClick: () -> Unit = {}
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
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.W500, color = contentColor)
            Text(subtitle, fontSize = 12.sp, color = Grey400)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Grey400.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
