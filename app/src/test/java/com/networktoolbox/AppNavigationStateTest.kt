package com.networktoolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        ToolScreen.PRIVACY,
        ToolScreen.ABOUT,
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
    fun drawerDestinationsAreSecondaryAndBackReturnsToItsCaller() {
        listOf(
            TopLevelDestination.HOME,
            TopLevelDestination.TOOLS,
            TopLevelDestination.DEVICES,
        ).forEach { caller ->
            listOf(ToolScreen.HISTORY, ToolScreen.PRIVACY, ToolScreen.ABOUT).forEach { screen ->
                    val state = AppNavigationState()
                        .selectTopLevel(caller)
                        .openSecondaryDestination(
                            screen = screen,
                            source = SecondaryNavigationSource.DRAWER,
                        )

                assertEquals(screen, state.toolScreen)
                val returned = state.goBack()
                assertEquals(caller, returned.topLevelDestination)
                assertEquals(ToolScreen.NONE, returned.toolScreen)
                assertTrue(returned.reopenDrawerRequest)
                assertEquals(
                    SecondaryNavigationSource.NONE,
                    returned.secondaryNavigationSource,
                )
            }
        }
    }

    @Test
    fun nonDrawerHistoryBack_returnsToCallerWithoutReopeningDrawer() {
        val returned = AppNavigationState()
            .selectTopLevel(TopLevelDestination.TOOLS)
            .openTool(ToolScreen.HISTORY)
            .goBack()

        assertEquals(TopLevelDestination.TOOLS, returned.topLevelDestination)
        assertFalse(returned.reopenDrawerRequest)
    }

    @Test
    fun drawerReopenRequest_isExplicitAndConsumedOnce() {
        val returned = AppNavigationState()
            .selectTopLevel(TopLevelDestination.DEVICES)
            .openSecondaryDestination(
                screen = ToolScreen.ABOUT,
                source = SecondaryNavigationSource.DRAWER,
            )
            .goBack()

        assertTrue(returned.reopenDrawerRequest)
        assertFalse(returned.consumeDrawerReopenRequest().reopenDrawerRequest)
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
            AppShellPresentation.canShowDrawer(
                AppNavigationState().openSecondaryDestination(ToolScreen.PRIVACY),
            ),
        )
        assertEquals(
            listOf("检测历史", "隐私与数据", "关于"),
            AppShellPresentation.drawerItemLabels(),
        )
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
