package com.wuling.keyless.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wuling.keyless.storage.KeyStorage
import com.wuling.keyless.ui.home.HomeScreen
import com.wuling.keyless.ui.login.LoginScreen
import com.wuling.keyless.ui.manual.ManualSetupScreen
import com.wuling.keyless.ui.profile.ProfileScreen
import com.wuling.keyless.ui.settings.SettingsScreen
import com.wuling.keyless.ui.setup.SetupScreen
import com.wuling.keyless.ui.theme.WulingTheme
import kotlinx.coroutines.launch

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WulingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("garage", "车库", Icons.Default.Garage),
    BottomNavItem("settings", "设置", Icons.Default.Settings),
    BottomNavItem("profile", "我的", Icons.Default.Person)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var startDest by remember { mutableStateOf("splash") }
    var isSetupDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val storage = KeyStorage(context)
        isSetupDone = storage.isSetupDone()
        startDest = if (isSetupDone) "main" else "setup"
    }

    val showBottomBar = currentRoute in listOf("garage", "settings", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 11.sp) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = com.wuling.keyless.ui.theme.Primary,
                                selectedTextColor = com.wuling.keyless.ui.theme.Primary,
                                indicatorColor = com.wuling.keyless.ui.theme.Primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(padding)
        ) {
            composable("splash") {
                SplashScreen(onComplete = {
                    navController.navigate(startDest) {
                        popUpTo("splash") { inclusive = true }
                    }
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
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                })
            }
            composable("manual-setup") {
                ManualSetupScreen(onSuccess = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                })
            }
            composable("main") {
                LaunchedEffect(Unit) {
                    navController.navigate("garage") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            }
            composable("garage") {
                HomeScreen(onSettings = {
                    navController.navigate("settings") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable("settings") {
                SettingsScreen(onReset = {
                    scope.launch {
                        KeyStorage(context).clearAll()
                        navController.navigate("setup") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                })
            }
            composable("profile") {
                ProfileScreen(onReset = {
                    scope.launch {
                        KeyStorage(context).clearAll()
                        Toast.makeText(context, "已清除所有数据", Toast.LENGTH_SHORT).show()
                        navController.navigate("setup") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                })
            }
        }
    }
}
