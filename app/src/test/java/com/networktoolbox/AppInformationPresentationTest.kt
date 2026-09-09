package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInformationPresentationTest {
    @Test
    fun drawerContainsOnlyAppLevelDestinations() {
        assertEquals(
            listOf("检测历史", "隐私与数据", "关于"),
            AppShellPresentation.drawerItemLabels(),
        )
        assertFalse(AppShellPresentation.drawerItemLabels().contains("设置"))
    }

    @Test
    fun drawerVersionUsesTheBuildVersion() {
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            AppShellPresentation.versionLabel(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun aboutUsesRealAppIdentityAndBuildVersion() {
        assertEquals("关于", AppInformationPresentation.aboutTitle)
        assertEquals("NetworkToolbox", AppInformationPresentation.appName)
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            AppInformationPresentation.versionValue(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun privacyCopyStatesLocalFirstNoAccountAndNoUpload() {
        assertTrue(AppInformationPresentation.localFirstDescription.contains("本地"))
        assertTrue(AppInformationPresentation.uploadDescription.contains("不会"))
        assertTrue(AppInformationPresentation.accountDescription.contains("无需账号"))
    }
}
