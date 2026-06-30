package com.wuling.keyless.ui.login

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.ui.theme.Danger
import com.wuling.keyless.ui.theme.Success
import com.wuling.keyless.ui.theme.Warning
import com.wuling.keyless.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    var tabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(result) {
        if (!result.isNullOrEmpty() && result != "ble_ok") onSuccess()
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(if (tabIndex == 0) "账号密码登录" else "Sgmw API 凭证")
        })
    }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("账号登录", fontSize = 14.sp) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("凭证模式", fontSize = 14.sp) }
                )
            }
            when (tabIndex) {
                0 -> PasswordLoginTab(viewModel, loading, error)
                1 -> CredentialTab(viewModel, loading, error)
            }
        }
    }
}

@Composable
private fun PasswordLoginTab(viewModel: LoginViewModel, loading: Boolean, error: String?) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
                Text(
                    "使用五菱App手机号和密码登录，自动获取 Token\n无需手动抓包，登录后自动完成 BLE 配置",
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
            label = { Text("手机号") },
            placeholder = { Text("输入五菱App绑定手机号") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            placeholder = { Text("输入五菱App登录密码") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Danger, fontSize = 14.sp)
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.loginWithPassword(phone, password) },
            enabled = !loading && phone.length >= 11 && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("登录中...", fontSize = 15.sp)
                }
            } else {
                Text("登录并自动配置", fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            color = Success.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("一键配置流程：", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("1. 验证手机号和密码", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("2. 自动获取 API 访问令牌", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("3. 从云端获取 BLE 密钥配置", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("4. 自动填充车辆 MAC / 密钥 / VIN", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun CredentialTab(viewModel: LoginViewModel, loading: Boolean, error: String?) {
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Warning.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Default.Info, null, tint = Warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "从五菱官方App抓包获取: junApi/sgmw/userCarRelation/queryDefaultCarStatus 请求头中的 sgmwaccesstoken / sgmwclientid / sgmwclientsecret",
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("sgmwaccesstoken") },
            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false, maxLines = 2
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = clientId, onValueChange = { clientId = it },
            label = { Text("sgmwclientid") },
            leadingIcon = { Icon(Icons.Default.Fingerprint, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = clientSecret, onValueChange = { clientSecret = it },
            label = { Text("sgmwclientsecret") },
            leadingIcon = { Icon(Icons.Default.Security, null) },
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { viewModel.validateAndSave(token, clientId, clientSecret) },
            enabled = !loading && token.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("验证并保存", fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "抓包工具推荐: HttpCanary (Android)、Stream (iOS)、Charles、mitmproxy",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}
