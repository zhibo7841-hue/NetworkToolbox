package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    private val toolScreens = listOf(
        ToolScreen.PING,
        ToolScreen.DNS,
        ToolScreen.TCP,
        ToolScreen.SUBNET,
        ToolScreen.TRACEROUTE,
        ToolScreen.LAN_SCAN,
        ToolScreen.REPORT,
        ToolScreen.HISTORY,
    )

    @Test
    fun toolsOpenedFromHome_returnToHomeForEveryTool() {
        toolScreens.forEach { tool ->
            val state = AppNavigationState()
                .selectTopLevel(TopLevelDestination.HOME)
                .openTool(tool)

            assertEquals(TopLevelDestination.HOME, state.goBack().topLevelDestination)
            assertEquals(ToolScreen.NONE, state.goBack().toolScreen)
        }
    }

    @Test
    fun toolsOpenedFromTools_returnToToolsForEveryTool() {
        toolScreens.forEach { tool ->
            val state = AppNavigationState()
                .selectTopLevel(TopLevelDestination.TOOLS)
                .openTool(tool)

            assertEquals(TopLevelDestination.TOOLS, state.goBack().topLevelDestination)
            assertEquals(ToolScreen.NONE, state.goBack().toolScreen)
        }
    }

    @Test
    fun topLevelDestinations_areHomeToolsAndDevices() {
        assertEquals(
            listOf("首页", "工具", "设备"),
            TopLevelDestination.entries.map(TopLevelDestination::label),
        )
        assertEquals(3, TopLevelDestination.entries.size)
        assertEquals(null, TopLevelDestination.entries.find { it.name == "SETTINGS" })
    }

    @Test
    fun settingsIsSecondaryAndBackReturnsToItsCaller() {
        listOf(
            TopLevelDestination.HOME,
            TopLevelDestination.TOOLS,
            TopLevelDestination.DEVICES,
        ).forEach { caller ->
            val state = AppNavigationState()
                .selectTopLevel(caller)
                .openSettings()

            assertEquals(ToolScreen.SETTINGS, state.toolScreen)
            assertEquals(caller, state.goBack().topLevelDestination)
            assertEquals(ToolScreen.NONE, state.goBack().toolScreen)
        }
    }

    @Test
    fun drawerIsAvailableOnlyForTopLevelDestinations() {
        assertEquals(true, AppShellPresentation.canShowDrawer(AppNavigationState()))
        assertEquals(
            false,
            AppShellPresentation.canShowDrawer(AppNavigationState().openTool(ToolScreen.PING)),
        )
        assertEquals(
            false,
            AppShellPresentation.canShowDrawer(AppNavigationState().openSettings()),
        )
        assertEquals(listOf("设置"), AppShellPresentation.drawerItems)
    }

    @Test
    fun reportOpenedFromHistory_preservesOriginalHomeOrigin() {
        val state = AppNavigationState()
            .openTool(ToolScreen.HISTORY)
            .openTool(ToolScreen.REPORT)

        assertEquals(TopLevelDestination.HOME, state.goBack().topLevelDestination)
    }

    @Test
    fun reportOpenedFromHistoryPreservesOriginalToolsOrigin() {
        val state = AppNavigationState()
            .selectTopLevel(TopLevelDestination.TOOLS)
            .openTool(ToolScreen.HISTORY)
            .openTool(ToolScreen.REPORT)

        assertEquals(TopLevelDestination.TOOLS, state.goBack().topLevelDestination)
    }

    @Test
    fun selectingBottomTab_closesToolAndDoesNotCreateBackStack() {
        val state = AppNavigationState()
            .openTool(ToolScreen.PING)
            .selectTopLevel(TopLevelDestination.DEVICES)

        assertEquals(TopLevelDestination.DEVICES, state.topLevelDestination)
        assertEquals(ToolScreen.NONE, state.toolScreen)
        assertEquals(state, state.goBack())
    }
}
