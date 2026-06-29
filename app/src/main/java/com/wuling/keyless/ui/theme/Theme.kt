package com.wuling.keyless.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Scheme = lightColorScheme(
    primary = Primary,
    secondary = Primary,
    background = Background,
    surface = Surface,
    onSurface = OnSurface,
)

@Composable
fun WulingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
