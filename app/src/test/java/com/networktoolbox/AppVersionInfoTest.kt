package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionInfoTest {
    @Test
    fun formatVersionNameUsesTheCurrentBuildVersion() {
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            AppVersionInfo.formatVersionName(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun formatVersionNameUsesSafeFallbackForBlankValue() {
        assertEquals("Version unknown", AppVersionInfo.formatVersionName("  "))
        assertEquals("Version unknown", AppVersionInfo.formatVersionName(null))
    }
}
