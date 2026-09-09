package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing

@Composable
internal fun AppShellDrawer(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(onOpenSettings = onOpenSettings)
            }
        },
        content = content,
    )
}

@Composable
private fun AppDrawerContent(
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = NetworkToolboxSpacing.XL,
                top = NetworkToolboxSpacing.LG,
                end = NetworkToolboxSpacing.XL,
                bottom = NetworkToolboxSpacing.XXL,
            ),
        verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
    ) {
        Text("NetworkToolbox", style = MaterialTheme.typography.titleLarge)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(NetworkToolboxSpacing.SM))
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text(AppShellPresentation.drawerSettingsLabel) },
            selected = false,
            onClick = onOpenSettings,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
