package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxComponentShapes
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation

/**
 * Shared compact device result surface for the configurable scanner and the
 * top-level Device Center.
 *
 * The card is informational, not clickable. It renders only identity and
 * discovery evidence already present in [LanDevice].
 */
@Composable
fun LanDeviceCard(
    device: LanDevice,
    modifier: Modifier = Modifier,
    showMac: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NetworkToolboxComponentShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = NetworkToolboxSpacing.LG,
                    vertical = NetworkToolboxSpacing.SM,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS),
            ) {
                Text(
                    LanScannerPresentation.deviceDisplayName(device),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    device.ipAddress,
                    style = NetworkToolboxTextStyles.TechnicalData,
                )
                LanScannerPresentation.deviceIdentitySummary(device)?.let { identity ->
                    Text(
                        identity,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LanScannerPresentation.deviceSecondaryText(device)?.let { evidence ->
                    Text(
                        evidence,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (showMac) {
                    device.macAddress?.takeIf(String::isNotBlank)?.let { mac ->
                        Text(
                            "MAC · $mac",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = NetworkToolboxTextStyles.TechnicalData,
                        )
                    }
                }
            }
            LanScannerPresentation.deviceRole(device)
                .takeIf(String::isNotBlank)
                ?.let { role ->
                    Text(
                        role,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
        }
    }
}
