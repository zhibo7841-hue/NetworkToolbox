package com.networktoolbox.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class NetworkToolboxThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

internal val LocalNetworkToolboxDarkTheme = staticCompositionLocalOf { false }

private val NetworkToolboxDarkColorScheme = darkColorScheme(
    primary = NetworkToolboxColors.DarkPrimary,
    onPrimary = Color(0xFF06204A),
    primaryContainer = Color(0xFF1A3867),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = NetworkToolboxColors.DarkSecondary,
    onSecondary = Color(0xFF07353B),
    secondaryContainer = Color(0xFF204B52),
    onSecondaryContainer = Color(0xFFB7EEF3),
    background = NetworkToolboxColors.DarkBackground,
    onBackground = NetworkToolboxColors.DarkPrimaryText,
    surface = NetworkToolboxColors.DarkSurface,
    onSurface = NetworkToolboxColors.DarkPrimaryText,
    surfaceVariant = NetworkToolboxColors.DarkSurfaceVariant,
    onSurfaceVariant = NetworkToolboxColors.DarkSecondaryText,
    outline = NetworkToolboxColors.DarkOutline,
    error = NetworkToolboxColors.DarkError,
    onError = Color(0xFF3B0710),
    errorContainer = Color(0xFF491D28),
    onErrorContainer = Color(0xFFFFDADF),
)

private val NetworkToolboxLightColorScheme = lightColorScheme(
    primary = NetworkToolboxColors.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0D2A63),
    secondary = NetworkToolboxColors.LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0EEF2),
    onSecondaryContainer = Color(0xFF0B343B),
    background = NetworkToolboxColors.LightBackground,
    onBackground = NetworkToolboxColors.LightPrimaryText,
    surface = NetworkToolboxColors.LightSurface,
    onSurface = NetworkToolboxColors.LightPrimaryText,
    surfaceVariant = NetworkToolboxColors.LightSurfaceVariant,
    onSurfaceVariant = NetworkToolboxColors.LightSecondaryText,
    outline = NetworkToolboxColors.LightOutline,
    error = NetworkToolboxColors.LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF601410),
)

private val BaseTypography = Typography()

val NetworkToolboxTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
)

val NetworkToolboxShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

internal fun networkToolboxLightColorScheme() = NetworkToolboxLightColorScheme

internal fun networkToolboxDarkColorScheme() = NetworkToolboxDarkColorScheme

internal fun resolveDarkTheme(
    themeMode: NetworkToolboxThemeMode,
    systemDarkTheme: Boolean,
): Boolean = when (themeMode) {
    NetworkToolboxThemeMode.SYSTEM -> systemDarkTheme
    NetworkToolboxThemeMode.LIGHT -> false
    NetworkToolboxThemeMode.DARK -> true
}

@Composable
fun NetworkToolboxTheme(
    themeMode: NetworkToolboxThemeMode = NetworkToolboxThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalNetworkToolboxDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                NetworkToolboxDarkColorScheme
            } else {
                NetworkToolboxLightColorScheme
            },
            typography = NetworkToolboxTypography,
            shapes = NetworkToolboxShapes,
            content = content,
        )
    }
}
