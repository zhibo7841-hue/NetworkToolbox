package com.networktoolbox.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NetworkToolAccent {
    PRIMARY,
    CYAN,
    AMBER,
}

data class NetworkToolAccentColorSet(
    val foreground: Color,
    val container: Color,
)

object NetworkToolAccents {
    fun resolve(accent: NetworkToolAccent, darkTheme: Boolean): NetworkToolAccentColorSet =
        when (accent) {
            NetworkToolAccent.PRIMARY -> NetworkToolAccentColorSet(
                foreground = if (darkTheme) {
                    NetworkToolboxColors.DarkPrimary
                } else {
                    NetworkToolboxColors.LightPrimary
                },
                container = if (darkTheme) {
                    NetworkToolboxColors.DarkPrimaryContainer
                } else {
                    NetworkToolboxColors.LightPrimaryContainer
                },
            )

            NetworkToolAccent.CYAN -> NetworkToolAccentColorSet(
                foreground = if (darkTheme) {
                    NetworkToolboxColors.DarkSecondary
                } else {
                    NetworkToolboxColors.LightSecondary
                },
                container = if (darkTheme) {
                    NetworkToolboxColors.DarkSecondaryContainer
                } else {
                    NetworkToolboxColors.LightSecondaryContainer
                },
            )

            NetworkToolAccent.AMBER -> NetworkToolAccentColorSet(
                foreground = if (darkTheme) {
                    NetworkToolboxColors.DarkNotice
                } else {
                    NetworkToolboxColors.LightNotice
                },
                container = if (darkTheme) {
                    NetworkToolboxColors.DarkNoticeContainer
                } else {
                    NetworkToolboxColors.LightNoticeContainer
                },
            )
        }
}

@Composable
fun ToolIconContainer(
    icon: ImageVector,
    accent: NetworkToolAccent,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = NetworkToolAccents.resolve(
        accent = accent,
        darkTheme = LocalNetworkToolboxDarkTheme.current,
    )
    Surface(
        modifier = modifier.size(40.dp),
        shape = NetworkToolboxComponentShapes.Chip,
        color = colors.container,
        contentColor = colors.foreground,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
