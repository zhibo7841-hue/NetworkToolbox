package com.networktoolbox

internal enum class AppShellDrawerItem(val label: String) {
    HISTORY("检测历史"),
    PRIVACY("隐私与数据"),
    ABOUT("关于"),
}

/** Small app-shell contract shared by the drawer and navigation tests. */
internal object AppShellPresentation {
    val drawerItems = AppShellDrawerItem.entries.toList()

    fun drawerItemLabels(): List<String> = drawerItems.map(AppShellDrawerItem::label)

    fun versionLabel(versionName: String?): String = AppVersionInfo.formatVersionName(versionName)

    fun canShowDrawer(state: AppNavigationState): Boolean =
        state.toolScreen == ToolScreen.NONE
}
