package com.networktoolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NetworkToolboxColorScheme = lightColorScheme()

@Composable
fun NetworkToolboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetworkToolboxColorScheme,
        content = content,
    )
}
