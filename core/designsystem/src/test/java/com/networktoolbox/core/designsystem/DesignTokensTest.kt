package com.networktoolbox.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {
    @Test
    fun darkColorSchemeUsesDeepNetworkBlueBaseline() {
        val scheme = networkToolboxDarkColorScheme()

        assertEquals(NetworkToolboxColors.DarkBackground, scheme.background)
        assertEquals(NetworkToolboxColors.DarkSurface, scheme.surface)
        assertEquals(NetworkToolboxColors.DarkSurfaceVariant, scheme.surfaceVariant)
        assertEquals(NetworkToolboxColors.DarkPrimary, scheme.primary)
        assertEquals(NetworkToolboxColors.DarkPrimaryText, scheme.onSurface)
        assertEquals(NetworkToolboxColors.DarkSecondaryText, scheme.onSurfaceVariant)
    }

    @Test
    fun lightColorSchemeUsesReadableLightBaseline() {
        val scheme = networkToolboxLightColorScheme()

        assertEquals(NetworkToolboxColors.LightBackground, scheme.background)
        assertEquals(NetworkToolboxColors.LightSurface, scheme.surface)
        assertEquals(NetworkToolboxColors.LightPrimary, scheme.primary)
        assertEquals(NetworkToolboxColors.LightPrimaryText, scheme.onSurface)
        assertEquals(NetworkToolboxColors.LightSecondaryText, scheme.onSurfaceVariant)
    }

    @Test
    fun themeModeResolvesSystemLightAndDark() {
        assertTrue(resolveDarkTheme(NetworkToolboxThemeMode.DARK, systemDarkTheme = false))
        assertFalse(resolveDarkTheme(NetworkToolboxThemeMode.LIGHT, systemDarkTheme = true))
        assertTrue(resolveDarkTheme(NetworkToolboxThemeMode.SYSTEM, systemDarkTheme = true))
        assertFalse(resolveDarkTheme(NetworkToolboxThemeMode.SYSTEM, systemDarkTheme = false))
    }

    @Test
    fun statusMappingKeepsSemanticLabelsAndThemeAwareColors() {
        assertEquals("正常", NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, false).label)
        assertEquals("提示", NetworkToolboxStatusVisuals.resolve(StatusVisualState.NOTICE, false).label)
        assertEquals("异常", NetworkToolboxStatusVisuals.resolve(StatusVisualState.WARNING, false).label)
        assertEquals("严重异常", NetworkToolboxStatusVisuals.resolve(StatusVisualState.ERROR, false).label)
        assertEquals("未确定", NetworkToolboxStatusVisuals.resolve(StatusVisualState.UNKNOWN, false).label)
        assertEquals("进行中", NetworkToolboxStatusVisuals.resolve(StatusVisualState.RUNNING, false).label)
        assertEquals("已停止", NetworkToolboxStatusVisuals.resolve(StatusVisualState.CANCELLED, false).label)
        assertEquals("未执行", NetworkToolboxStatusVisuals.resolve(StatusVisualState.NOT_EXECUTED, false).label)
        assertEquals(
            NetworkToolboxColors.DarkSuccess,
            NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, true).foregroundColor,
        )
        assertEquals(
            NetworkToolboxColors.LightSuccess,
            NetworkToolboxStatusVisuals.resolve(StatusVisualState.NORMAL, false).foregroundColor,
        )
    }
}
