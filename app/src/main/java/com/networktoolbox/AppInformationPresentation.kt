package com.networktoolbox

/** User-facing copy for the Drawer-owned information destinations. */
internal object AppInformationPresentation {
    const val aboutTitle = "关于"
    const val aboutDescription = "NetworkToolbox 应用信息"
    const val appName = "NetworkToolbox"
    const val appDescription = "Open Source Network Analyzer"
    const val versionTitle = "版本"
    const val versionSupport = "当前应用版本"

    const val privacyTitle = "隐私与数据"
    const val privacyDescription = "了解检测数据如何保存在本机。"
    const val localFirstTitle = "本地优先"
    const val localFirstDescription = "诊断数据和检测历史仅保存在设备本地。"
    const val uploadTitle = "数据上传"
    const val uploadDescription = "NetworkToolbox 不会将诊断结果和历史记录上传到服务器。"
    const val accountTitle = "账号要求"
    const val accountDescription = "核心功能无需账号即可使用。"

    fun versionValue(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)
}
