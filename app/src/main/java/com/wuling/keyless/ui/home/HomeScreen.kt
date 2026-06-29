package com.wuling.keyless.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.service.DoorState
import com.wuling.keyless.ui.theme.*
import com.wuling.keyless.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val autoUnlock by viewModel.autoUnlock.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLock.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("五菱无感控车") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            StatusRing(rssi = status.rssi, label = status.label, isNear = status.isNear, isFar = status.isFar, doorState = status.doorState)
            Spacer(Modifier.height(32.dp))
            BottomCards(autoUnlock = autoUnlock, autoLock = autoLock,
                isNear = status.isNear, doorLocked = status.doorState == DoorState.LOCKED)
            Spacer(Modifier.weight(1f))
            LogSection(logs = logs, maxLines = 10)
        }
    }
}

@Composable
private fun StatusRing(rssi: Int, label: String, isNear: Boolean, isFar: Boolean, doorState: DoorState) {
    val color = when {
        doorState == DoorState.UNLOCKED -> Success
        isNear -> Warning
        isFar -> Danger
        rssi >= -100 -> Color.Blue
        else -> Grey400
    }
    val icon = when {
        doorState == DoorState.UNLOCKED -> Icons.Default.LockOpen
        isNear -> Icons.Default.BluetoothConnected
        isFar -> Icons.Default.Lock
        rssi >= -100 -> Icons.Default.Bluetooth
        else -> Icons.Default.BluetoothSearching
    }
    val statusText = when {
        doorState == DoorState.UNLOCKED -> "已开锁"
        isNear -> "已靠近"
        isFar -> "已落锁"
        rssi >= -100 -> "中等距离"
        else -> "正在搜索车辆..."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(140.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
                .border(3.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(56.dp), tint = color)
        }
        Spacer(Modifier.height(16.dp))
        Text(statusText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (rssi >= -100) Text("信号 $rssi dBm", fontSize = 14.sp, color = Grey400)
    }
}

@Composable
private fun BottomCards(autoUnlock: Boolean, autoLock: Boolean, isNear: Boolean, doorLocked: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(
            icon = Icons.Default.LockOpen, label = "靠近开锁",
            active = autoUnlock && isNear, color = Success,
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            icon = Icons.Default.Lock, label = "离开落锁",
            active = autoLock && doorLocked, color = Danger,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, color: Color, modifier: Modifier) {
    val bgColor by animateColorAsState(if (active) color.copy(alpha = 0.1f) else Grey400.copy(alpha = 0.1f))
    val borderColor by animateColorAsState(if (active) color else Grey400)
    val contentColor by animateColorAsState(if (active) color else Grey400)

    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(bgColor).border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.W500, color = contentColor)
    }
}

@Composable
private fun LogSection(logs: List<String>, maxLines: Int) {
    if (logs.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
            .padding(8.dp)
    ) {
        items(logs.takeLast(maxLines)) { msg ->
            Text(msg, fontSize = 11.sp, color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace)
        }
    }
    Spacer(Modifier.height(8.dp))
}
