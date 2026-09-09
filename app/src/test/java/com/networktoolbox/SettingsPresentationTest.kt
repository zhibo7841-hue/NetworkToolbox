package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun screenUsesTheConfirmedSettingsSections() {
        assertEquals(
            listOf("关于", "数据管理", "隐私保护"),
            SettingsPresentation.sectionTitles,
        )
        assertEquals("设置", SettingsPresentation.screenTitle)
        assertEquals("应用信息、数据与隐私", SettingsPresentation.screenDescription)
    }

    @Test
    fun aboutKeepsRealAppIdentityAndBuildVersion() {
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("NetworkToolbox"))
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("版本"))
        assertEquals(
            "Version ${BuildConfig.VERSION_NAME}",
            SettingsPresentation.versionValue(BuildConfig.VERSION_NAME),
        )
    }

    @Test
    fun dataManagementKeepsLocalHistoryAndDestructiveClearCopy() {
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("历史记录"))
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("清空历史"))
        assertTrue(SettingsPresentation.historySupport.contains("本机"))
        assertTrue(SettingsPresentation.clearHistorySupport.contains("删除"))
        assertEquals("清空", SettingsPresentation.clearActionLabel(isClearing = false))
        assertEquals("清理中...", SettingsPresentation.clearActionLabel(isClearing = true))
        assertTrue(SettingsPresentation.clearDialogText.contains("无法撤销"))
    }

    @Test
    fun privacyCopyStatesLocalFirstNoAccountAndNoUpload() {
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("本地优先"))
        assertTrue(SettingsPresentation.confirmedRowTitles.contains("数据上传"))
        assertTrue(SettingsPresentation.localFirstSupport.contains("本机"))
        assertTrue(SettingsPresentation.privacySupport.contains("无需账号"))
        assertTrue(SettingsPresentation.privacySupport.contains("不会上传"))
    }

    @Test
    fun settingsDoesNotInventAdditionalControls() {
        val forbiddenControls = setOf("语言", "主题", "分析", "云同步", "自动更新")

        assertFalse(SettingsPresentation.confirmedRowTitles.any(forbiddenControls::contains))
    }
}
