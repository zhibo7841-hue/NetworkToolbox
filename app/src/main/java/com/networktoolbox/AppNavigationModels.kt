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

internal enum class SecondaryNavigationSource {
    NONE,
    DRAWER,
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
    val secondaryNavigationSource: SecondaryNavigationSource = SecondaryNavigationSource.NONE,
    val reopenDrawerRequest: Boolean = false,
) {
    fun openTool(screen: ToolScreen): AppNavigationState = copy(
        topLevelDestination = TopLevelDestination.TOOLS,
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        secondaryNavigationSource = SecondaryNavigationSource.NONE,
        reopenDrawerRequest = false,
    )

    fun openSecondaryDestination(
        screen: ToolScreen,
        source: SecondaryNavigationSource = SecondaryNavigationSource.NONE,
    ): AppNavigationState = copy(
        toolScreen = screen,
        toolOrigin = if (toolScreen == ToolScreen.NONE) {
            topLevelDestination.navigationOrigin()
        } else {
            toolOrigin
        },
        secondaryNavigationSource = source,
        reopenDrawerRequest = false,
    )

    fun selectTopLevel(destination: TopLevelDestination): AppNavigationState = copy(
        topLevelDestination = destination,
        toolScreen = ToolScreen.NONE,
        secondaryNavigationSource = SecondaryNavigationSource.NONE,
        reopenDrawerRequest = false,
    )

    fun goBack(): AppNavigationState = if (toolScreen == ToolScreen.NONE) {
        this
    } else {
        copy(
            topLevelDestination = toolOrigin.backDestination(),
            toolScreen = ToolScreen.NONE,
            secondaryNavigationSource = SecondaryNavigationSource.NONE,
            reopenDrawerRequest = secondaryNavigationSource == SecondaryNavigationSource.DRAWER,
        )
    }

    fun consumeDrawerReopenRequest(): AppNavigationState = copy(reopenDrawerRequest = false)

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.topLevelDestination.name,
                    state.toolScreen.name,
                    state.toolOrigin.name,
                    state.secondaryNavigationSource.name,
                    state.reopenDrawerRequest.toString(),
                )
            },
            restore = { saved ->
                AppNavigationState(
                    topLevelDestination = TopLevelDestination.valueOf(saved[0]),
                    toolScreen = ToolScreen.valueOf(saved[1]),
                    toolOrigin = NavigationOrigin.valueOf(saved[2]),
                    secondaryNavigationSource = saved.getOrNull(3)
                        ?.let { SecondaryNavigationSource.valueOf(it) }
                        ?: SecondaryNavigationSource.NONE,
                    reopenDrawerRequest = saved.getOrNull(4) == "true",
                )
            },
        )
    }
}
