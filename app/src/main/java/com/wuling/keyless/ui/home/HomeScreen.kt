package com.wuling.keyless.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.service.ConnectionState
import com.wuling.keyless.service.DoorState
import com.wuling.keyless.ui.theme.*
import com.wuling.keyless.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val autoUnlock by viewModel.autoUnlock.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLock.collectAsStateWithLifecycle()
    val smartKeyEnabled by viewModel.smartKeyEnabled.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("车库", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Smart Key Toggle Card
            SmartKeyCard(
                enabled = smartKeyEnabled,
                isRunning = status.connectionState == ConnectionState.SCANNING ||
                           status.connectionState == ConnectionState.CONNECTED,
                onToggle = { viewModel.setSmartKeyEnabled(it) }
            )

            Spacer(Modifier.height(12.dp))

            // Vehicle Card
            VehicleCard(
                carName = "月落星光",
                connectionState = status.connectionState,
                doorState = status.doorState,
                rssi = status.rssi,
                label = status.label,
                lastError = status.lastError,
                onLock = { viewModel.manualLock() },
                onUnlock = { viewModel.manualUnlock() },
                onPark = { viewModel.manualPark() }
            )

            Spacer(Modifier.height(12.dp))

            // Auto control cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AutoControlCard(
                    icon = Icons.Default.LockOpen,
                    label = "靠近开锁",
                    enabled = autoUnlock,
                    isActive = status.isNear,
                    color = Success,
                    onToggle = { viewModel.setAutoUnlock(it) },
                    modifier = Modifier.weight(1f)
                )
                AutoControlCard(
                    icon = Icons.Default.Lock,
                    label = "离开落锁",
                    enabled = autoLock,
                    isActive = status.doorState == DoorState.LOCKED,
                    color = Danger,
                    onToggle = { viewModel.setAutoLock(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Log section
            if (logs.isNotEmpty()) {
                LogSection(logs = logs)
            }

            // Toast placeholder
            if (toastMessage != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = if (toastMessage!!.contains("成功")) Success.copy(alpha = 0.9f) else Warning.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        toastMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartKeyCard(
    enabled: Boolean,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) Primary.copy(alpha = 0.1f) else Grey400.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (enabled) Primary else Grey400,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("智能钥匙", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (enabled && isRunning) Success else Grey400)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (enabled && isRunning) "服务运行中" else if (enabled) "正在启动..." else "服务已关闭",
                        fontSize = 12.sp,
                        color = Grey400
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary
                )
            )
        }
    }
}

@Composable
private fun VehicleCard(
    carName: String,
    connectionState: ConnectionState,
    doorState: DoorState,
    rssi: Int,
    label: String,
    lastError: String?,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onPark: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with car name and status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(carName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (isConnected) Success else Grey400,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                isConnecting -> "连接中..."
                                isConnected -> "已连接  $rssi dBm"
                                rssi > -100 -> "信号 $rssi dBm"
                                else -> "未连接"
                            },
                            fontSize = 13.sp,
                            color = if (isConnected) Success else Grey400
                        )
                    }
                }
                // Lock status icon
                Surface(
                    shape = CircleShape,
                    color = when {
                        doorState == DoorState.UNLOCKED -> Success.copy(alpha = 0.1f)
                        doorState == DoorState.LOCKED -> Danger.copy(alpha = 0.1f)
                        else -> Grey400.copy(alpha = 0.1f)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (doorState == DoorState.UNLOCKED) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = when {
                                doorState == DoorState.UNLOCKED -> Success
                                doorState == DoorState.LOCKED -> Danger
                                else -> Grey400
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Car image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Primary.copy(alpha = 0.05f), Primary.copy(alpha = 0.15f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = if (rssi > -100) Primary else Grey400
                    )
                }
            }

            // Error message
            if (lastError != null) {
                Surface(
                    color = Danger.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        lastError,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        color = Danger
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    icon = Icons.Default.LockOpen,
                    label = "解锁",
                    enabled = !isConnecting,
                    color = Success,
                    onClick = onUnlock,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Default.Lock,
                    label = "上锁",
                    enabled = !isConnecting,
                    color = Danger,
                    onClick = onLock,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Default.LocalParking,
                    label = "泊车",
                    enabled = !isConnecting,
                    color = Warning,
                    onClick = onPark,
                    modifier = Modifier.weight(1f)
                )
            }

            // Hint text
            Text(
                "点击卡片进入手动控制 · 长按卡片进入编辑",
                fontSize = 11.sp,
                color = Grey400.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (enabled) color.copy(alpha = 0.1f) else Grey400.copy(alpha = 0.05f)
    )
    val contentColor by animateColorAsState(
        if (enabled) color else Grey400
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, contentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.W500, color = contentColor)
    }
}

@Composable
private fun AutoControlCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    isActive: Boolean,
    color: Color,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled && isActive) color else if (enabled) Grey400 else Grey400.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.W500)
                Text(
                    if (enabled) "自动" else "已关闭",
                    fontSize = 11.sp,
                    color = Grey400
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun LogSection(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(8.dp)
    ) {
        items(logs.takeLast(20)) { msg ->
            Text(msg, fontSize = 10.sp, color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace)
        }
    }
}
