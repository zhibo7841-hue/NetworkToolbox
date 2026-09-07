package com.networktoolbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class NavigationOrigin {
    HOME,
    TOOLS,
}

internal enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Outlined.Home),
    TOOLS("工具", Icons.Outlined.Build),
    SETTINGS("设置", Icons.Outlined.Settings),
}

internal enum class ToolScreen {
    NONE,
    SUBNET,
    PING,
    DNS,
    TCP,
    TRACEROUTE,
    REPORT,
    HISTORY,
    LAN_SCAN,
}

internal fun TopLevelDestination.navigationOrigin(): NavigationOrigin = when (this) {
    TopLevelDestination.HOME -> NavigationOrigin.HOME
    TopLevelDestination.TOOLS,
    TopLevelDestination.SETTINGS,
    -> NavigationOrigin.TOOLS
}

internal fun NavigationOrigin.backDestination(): TopLevelDestination = when (this) {
    NavigationOrigin.HOME -> TopLevelDestination.HOME
    NavigationOrigin.TOOLS -> TopLevelDestination.TOOLS
}

/**
 * The app shell has one tool surface but two real callers: Home and Tools.
 * Keeping the origin beside the tool destination prevents each feature screen
 * from inventing its own back behavior.
 */
internal data class AppNavigationState(
    val topLevelDestination: TopLevelDestination = TopLevelDestination.HOME,
    val toolScreen: ToolScreen = ToolScreen.NONE,
    val toolOrigin: NavigationOrigin = NavigationOrigin.HOME,
) {
    fun openTool(screen: ToolScreen): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.TOOLS,
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
    )

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState = copy(
        topLevelDestination = destination,
        toolScreen = ToolScreen.NONE,
    )

    fun goBack(): AppNavigationState = if (toolScreen == ToolScreen.NONE) {
        this
    } else {
        copy(
            topLevelDestination = toolOrigin.backDestination(),
            toolScreen = ToolScreen.NONE,
        )
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.topLevelDestination.name,
                    state.toolScreen.name,
                    state.toolOrigin.name,
                )
            },
            restore = { saved ->
                AppNavigationState(
                    topLevelDestination = TopLevelDestination.valueOf(saved[0]),
                    toolScreen = ToolScreen.valueOf(saved[1]),
                    toolOrigin = NavigationOrigin.valueOf(saved[2]),
                )
            },
        )
    }
}
