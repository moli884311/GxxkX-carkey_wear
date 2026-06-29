package com.wuling.keyless.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wuling.keyless.storage.KeyStorage
import com.wuling.keyless.ui.home.HomeScreen
import com.wuling.keyless.ui.login.LoginScreen
import com.wuling.keyless.ui.manual.ManualSetupScreen
import com.wuling.keyless.ui.settings.SettingsScreen
import com.wuling.keyless.ui.setup.SetupScreen
import com.wuling.keyless.ui.theme.WulingTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WulingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var startDest by remember { mutableStateOf("splash") }

    LaunchedEffect(Unit) {
        val storage = KeyStorage(context)
        val isSetup = storage.isSetupDone()
        startDest = if (isSetup) "home" else "setup"
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onComplete = {
                navController.navigate(startDest) { popUpTo("splash") { inclusive = true } }
            })
        }
        composable("setup") {
            SetupScreen(
                onSgmw = { navController.navigate("login") },
                onBle = { navController.navigate("manual-setup") }
            )
        }
        composable("login") {
            LoginScreen(onSuccess = {
                navController.navigate("home") { popUpTo(0) { inclusive = true } }
            })
        }
        composable("manual-setup") {
            ManualSetupScreen(onSuccess = {
                navController.navigate("home") { popUpTo(0) { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(onSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onReset = {
                scope.launch {
                    KeyStorage(context).clearAll()
                    navController.navigate("setup") { popUpTo(0) { inclusive = true } }
                }
            })
        }
    }
}
