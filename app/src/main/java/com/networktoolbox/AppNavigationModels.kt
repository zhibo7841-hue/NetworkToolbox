package com.networktoolbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class NavigationOrigin {
    HOME,
    TOOLS,
    DEVICES,
}

internal enum class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", Icons.Outlined.Home),
    TOOLS("工具", Icons.Outlined.Build),
    DEVICES("设备", Icons.Outlined.Lan),
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
    PRIVACY,
    ABOUT,
    LAN_SCAN,
}

internal fun TopLevelDestination.navigationOrigin(): NavigationOrigin = when (this) {
    TopLevelDestination.HOME -> NavigationOrigin.HOME
    TopLevelDestination.TOOLS -> NavigationOrigin.TOOLS
    TopLevelDestination.DEVICES -> NavigationOrigin.DEVICES
}

internal fun NavigationOrigin.backDestination(): TopLevelDestination = when (this) {
    NavigationOrigin.HOME -> TopLevelDestination.HOME
    NavigationOrigin.TOOLS -> TopLevelDestination.TOOLS
    NavigationOrigin.DEVICES -> TopLevelDestination.DEVICES
}

/**
 * The app shell has one tool surface with source-aware top-level callers.
 * Keeping the origin beside the tool destination prevents each feature screen
 * from inventing its own back behavior.
 */
internal data class AppNavigationState(
    val topLevelDestination: TopLevelDestination = TopLevelDestination.HOME,
    val toolScreen: ToolScreen = ToolScreen.NONE,
    val toolOrigin: NavigationOrigin = NavigationOrigin.HOME,
    val toolBackDestination: ToolScreen = ToolScreen.NONE,
) {
    fun openTool(screen: ToolScreen): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.TOOLS,
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        toolBackDestination = toolScreen.takeIf { it != ToolScreen.NONE } ?: ToolScreen.NONE,
    )

    fun openSecondaryDestination(screen: ToolScreen): AppNavigationState = copy(
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        toolBackDestination = ToolScreen.NONE,
    )

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState = copy(
        topLevelDestination = destination,
        toolScreen = ToolScreen.NONE,
        toolBackDestination = ToolScreen.NONE,
    )

    fun goBack(): AppNavigationState = when {
        toolScreen == ToolScreen.NONE -> this
        toolBackDestination != ToolScreen.NONE -> copy(
            toolScreen = toolBackDestination,
            toolBackDestination = ToolScreen.NONE,
        )
        else -> copy(
            topLevelDestination = toolOrigin.backDestination(),
            toolScreen = ToolScreen.NONE,
            toolBackDestination = ToolScreen.NONE,
        )
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.topLevelDestination.name,
                    state.toolScreen.name,
                    state.toolOrigin.name,
                    state.toolBackDestination.name,
                )
            },
            restore = { saved ->
                AppNavigationState(
                    topLevelDestination = TopLevelDestination.valueOf(saved[0]),
                    toolScreen = ToolScreen.valueOf(saved[1]),
                    toolOrigin = NavigationOrigin.valueOf(saved[2]),
                    // Slot 3 used to hold the Drawer source in the 063-B
                    // save format. Unknown values (including DRAWER) safely
                    // restore as no nested tool destination.
                    toolBackDestination = saved.getOrNull(3)
                        ?.let { value -> runCatching { ToolScreen.valueOf(value) }.getOrNull() }
                        ?: ToolScreen.NONE,
                )
            },
        )
    }
}
