package com.networktoolbox

/** Small app-shell contract shared by the drawer and navigation tests. */
internal object AppShellPresentation {
    const val drawerSettingsLabel = "设置"
    const val devicesDescription = "发现并查看局域网设备。"

    val drawerItems = listOf(drawerSettingsLabel)

    fun canShowDrawer(state: AppNavigationState): Boolean =
        state.toolScreen == ToolScreen.NONE
}
