package com.networktoolbox

/**
 * User-facing Settings copy and structure. Keeping this small contract
 * separate from the composables makes the confirmed scope easy to test.
 */
internal object SettingsPresentation {
    const val screenTitle = "设置"
    const val screenDescription = "应用信息、数据与隐私"

    const val aboutSectionTitle = "关于"
    const val appName = "NetworkToolbox"
    const val appDescription = "Open Source Network Analyzer"
    const val versionTitle = "版本"
    const val versionSupport = "当前应用版本"

    const val dataSectionTitle = "数据管理"
    const val historyTitle = "历史记录"
    const val historySupport = "检测历史仅保存在本机。"
    const val clearHistoryTitle = "清空历史"
    const val clearHistorySupport = "删除本机保存的检测记录。"
    const val clearingHistoryLabel = "清理中..."
    const val clearHistoryLabel = "清空"

    const val privacySectionTitle = "隐私保护"
    const val localFirstTitle = "本地优先"
    const val localFirstSupport = "诊断数据和历史记录仅保留在本机。"
    const val privacyTitle = "数据上传"
    const val privacySupport = "无需账号，网络检测结果和历史记录不会上传。"

    const val clearDialogTitle = "清空全部历史记录？"
    const val clearDialogText = "所有本地检测历史都会被删除，此操作无法撤销。"
    const val cancelLabel = "取消"

    val sectionTitles = listOf(
        aboutSectionTitle,
        dataSectionTitle,
        privacySectionTitle,
    )

    val confirmedRowTitles = listOf(
        appName,
        versionTitle,
        historyTitle,
        clearHistoryTitle,
        localFirstTitle,
        privacyTitle,
    )

    fun versionValue(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)

    fun clearActionLabel(isClearing: Boolean): String =
        if (isClearing) clearingHistoryLabel else clearHistoryLabel
}
