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
    onPrimary = NetworkToolboxColors.DarkOnPrimary,
    primaryContainer = NetworkToolboxColors.DarkPrimaryContainer,
    onPrimaryContainer = NetworkToolboxColors.DarkOnPrimaryContainer,
    inversePrimary = NetworkToolboxColors.DarkInversePrimary,
    secondary = NetworkToolboxColors.DarkSecondary,
    onSecondary = NetworkToolboxColors.DarkOnSecondary,
    secondaryContainer = NetworkToolboxColors.DarkSecondaryContainer,
    onSecondaryContainer = NetworkToolboxColors.DarkOnSecondaryContainer,
    tertiary = NetworkToolboxColors.DarkSecondary,
    onTertiary = NetworkToolboxColors.DarkOnSecondary,
    tertiaryContainer = NetworkToolboxColors.DarkSecondaryContainer,
    onTertiaryContainer = NetworkToolboxColors.DarkOnSecondaryContainer,
    surfaceDim = NetworkToolboxColors.DarkSurfaceDim,
    surfaceBright = NetworkToolboxColors.DarkSurfaceBright,
    surfaceContainerLowest = NetworkToolboxColors.DarkSurfaceContainerLowest,
    surfaceContainerLow = NetworkToolboxColors.DarkSurfaceContainerLow,
    surfaceContainer = NetworkToolboxColors.DarkSurface,
    surfaceContainerHigh = NetworkToolboxColors.DarkSurfaceContainerHigh,
    surfaceContainerHighest = NetworkToolboxColors.DarkSurfaceContainerHighest,
    background = NetworkToolboxColors.DarkBackground,
    onBackground = NetworkToolboxColors.DarkPrimaryText,
    surface = NetworkToolboxColors.DarkSurface,
    onSurface = NetworkToolboxColors.DarkPrimaryText,
    surfaceVariant = NetworkToolboxColors.DarkSurfaceVariant,
    onSurfaceVariant = NetworkToolboxColors.DarkSecondaryText,
    outline = NetworkToolboxColors.DarkOutline,
    outlineVariant = NetworkToolboxColors.DarkOutlineVariant,
    error = NetworkToolboxColors.DarkError,
    onError = NetworkToolboxColors.DarkOnError,
    errorContainer = NetworkToolboxColors.DarkErrorContainer,
    onErrorContainer = NetworkToolboxColors.DarkOnErrorContainer,
    inverseSurface = NetworkToolboxColors.DarkInverseSurface,
    inverseOnSurface = NetworkToolboxColors.DarkInverseOnSurface,
)

private val NetworkToolboxLightColorScheme = lightColorScheme(
    primary = NetworkToolboxColors.LightPrimary,
    onPrimary = NetworkToolboxColors.LightOnPrimary,
    primaryContainer = NetworkToolboxColors.LightPrimaryContainer,
    onPrimaryContainer = NetworkToolboxColors.LightOnPrimaryContainer,
    inversePrimary = NetworkToolboxColors.LightInversePrimary,
    secondary = NetworkToolboxColors.LightSecondary,
    onSecondary = NetworkToolboxColors.LightOnSecondary,
    secondaryContainer = NetworkToolboxColors.LightSecondaryContainer,
    onSecondaryContainer = NetworkToolboxColors.LightOnSecondaryContainer,
    tertiary = NetworkToolboxColors.LightSecondary,
    onTertiary = NetworkToolboxColors.LightOnSecondary,
    tertiaryContainer = NetworkToolboxColors.LightSecondaryContainer,
    onTertiaryContainer = NetworkToolboxColors.LightOnSecondaryContainer,
    surfaceDim = NetworkToolboxColors.LightSurfaceDim,
    surfaceBright = NetworkToolboxColors.LightSurfaceBright,
    surfaceContainerLowest = NetworkToolboxColors.LightSurfaceContainerLowest,
    surfaceContainerLow = NetworkToolboxColors.LightSurfaceContainerLow,
    surfaceContainer = NetworkToolboxColors.LightSurface,
    surfaceContainerHigh = NetworkToolboxColors.LightSurfaceContainerHigh,
    surfaceContainerHighest = NetworkToolboxColors.LightSurfaceContainerHighest,
    background = NetworkToolboxColors.LightBackground,
    onBackground = NetworkToolboxColors.LightPrimaryText,
    surface = NetworkToolboxColors.LightSurface,
    onSurface = NetworkToolboxColors.LightPrimaryText,
    surfaceVariant = NetworkToolboxColors.LightSurfaceVariant,
    onSurfaceVariant = NetworkToolboxColors.LightSecondaryText,
    outline = NetworkToolboxColors.LightOutline,
    outlineVariant = NetworkToolboxColors.LightOutlineVariant,
    error = NetworkToolboxColors.LightError,
    onError = NetworkToolboxColors.LightOnError,
    errorContainer = NetworkToolboxColors.LightErrorContainer,
    onErrorContainer = NetworkToolboxColors.LightOnErrorContainer,
    inverseSurface = NetworkToolboxColors.LightInverseSurface,
    inverseOnSurface = NetworkToolboxColors.LightInverseOnSurface,
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
