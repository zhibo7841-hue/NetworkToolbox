package com.networktoolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.ToolIconContainer
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = AppInformationPresentation.aboutTitle,
            description = AppInformationPresentation.aboutDescription,
            icon = Icons.Outlined.Info,
            accent = NetworkToolAccent.PRIMARY,
            onBack = onBack,
        )

        OutlinedNetworkCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NetworkToolboxSpacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
            ) {
                ToolIconContainer(
                    icon = Icons.Outlined.Lan,
                    accent = NetworkToolAccent.PRIMARY,
                    contentDescription = AppInformationPresentation.appName,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
                ) {
                    Text(
                        AppInformationPresentation.appName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        AppInformationPresentation.appDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            InformationRow(
                title = AppInformationPresentation.versionTitle,
                supportingText = AppInformationPresentation.versionSupport,
                value = AppInformationPresentation.versionValue(BuildConfig.VERSION_NAME),
            )
        }
    }
}

@Composable
internal fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = AppInformationPresentation.privacyTitle,
            description = AppInformationPresentation.privacyDescription,
            icon = Icons.Outlined.Lock,
            accent = NetworkToolAccent.CYAN,
            onBack = onBack,
        )

        InformationCard(
            title = AppInformationPresentation.localFirstTitle,
            description = AppInformationPresentation.localFirstDescription,
        )
        InformationCard(
            title = AppInformationPresentation.uploadTitle,
            description = AppInformationPresentation.uploadDescription,
        )
        InformationCard(
            title = AppInformationPresentation.accountTitle,
            description = AppInformationPresentation.accountDescription,
        )
    }
}

@Composable
private fun InformationCard(
    title: String,
    description: String,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InformationRow(
    title: String,
    supportingText: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
