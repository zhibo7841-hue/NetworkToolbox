package com.networktoolbox.core.designsystem

import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class NetworkToolboxNavigationColorSet(
    val selectedIcon: Color,
    val selectedLabel: Color,
    val selectedIndicator: Color,
    val unselectedIcon: Color,
    val unselectedLabel: Color,
)

object NetworkToolboxNavigationColors {
    fun resolve(darkTheme: Boolean): NetworkToolboxNavigationColorSet {
        return if (darkTheme) {
            NetworkToolboxNavigationColorSet(
                selectedIcon = NetworkToolboxColors.DarkPrimary,
                selectedLabel = NetworkToolboxColors.DarkPrimary,
                selectedIndicator = NetworkToolboxColors.DarkPrimaryContainer,
                unselectedIcon = NetworkToolboxColors.DarkSecondaryText,
                unselectedLabel = NetworkToolboxColors.DarkSecondaryText,
            )
        } else {
            NetworkToolboxNavigationColorSet(
                selectedIcon = NetworkToolboxColors.LightPrimary,
                selectedLabel = NetworkToolboxColors.LightPrimary,
                selectedIndicator = NetworkToolboxColors.LightPrimaryContainer,
                unselectedIcon = NetworkToolboxColors.LightSecondaryText,
                unselectedLabel = NetworkToolboxColors.LightSecondaryText,
            )
        }
    }
}

@Composable
fun networkToolboxNavigationItemColors(): NavigationBarItemColors {
    val colors = NetworkToolboxNavigationColors.resolve(LocalNetworkToolboxDarkTheme.current)
    return NavigationBarItemDefaults.colors(
        selectedIconColor = colors.selectedIcon,
        selectedTextColor = colors.selectedLabel,
        indicatorColor = colors.selectedIndicator,
        unselectedIconColor = colors.unselectedIcon,
        unselectedTextColor = colors.unselectedLabel,
    )
}
