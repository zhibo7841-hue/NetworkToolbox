package com.networktoolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetworkToolboxColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF006874),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF1A1B20),
    error = Color(0xFFBA1A1A),
)

private val NetworkToolboxTypography = Typography()

@Composable
fun NetworkToolboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetworkToolboxColorScheme,
        typography = NetworkToolboxTypography,
        content = content,
    )
}
