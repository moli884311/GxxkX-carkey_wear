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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuling.keyless.ui.theme.Danger
import com.wuling.keyless.ui.theme.Success
import com.wuling.keyless.ui.theme.Warning
import com.wuling.keyless.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }

    LaunchedEffect(result) {
        if (!result.isNullOrEmpty() && result != "ble_ok") onSuccess()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sgmw API 凭证") }) }) {
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
                Text(error!!, color = Danger, fontSize = 14.sp)
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
}
