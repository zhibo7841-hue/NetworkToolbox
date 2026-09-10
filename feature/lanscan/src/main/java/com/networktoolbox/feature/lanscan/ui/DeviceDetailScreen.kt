package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.SecondaryInformationHeader
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.feature.lanscan.presentation.DeviceDetailPresentation
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceDetailScreen(
    detail: DeviceDetailPresentation?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    favoriteErrorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    ToolScreenLayout(modifier = modifier) {
        SecondaryInformationHeader(
            title = "设备详情",
            onBack = onBack,
            trailingContent = {
                if (detail?.canToggleFavorite == true) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier,
                    ) {
                        Icon(
                            imageVector = if (detail.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = detail.favoriteToggleContentDescription,
                            tint = if (detail.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            },
        )

        if (detail == null) {
            OutlinedNetworkCard {
                Text("设备信息已不可用", style = MaterialTheme.typography.titleMedium)
                Text(
                    "该设备不在当前扫描结果或本地收藏中。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ToolScreenLayout
        }

        OutlinedNetworkCard {
            Text(detail.displayName, style = MaterialTheme.typography.headlineSmall)
            detail.ipAddress?.takeIf(String::isNotBlank)?.let { ip ->
                Text(ip, style = NetworkToolboxTextStyles.TechnicalData)
            }
            detail.role?.takeIf(String::isNotBlank)?.let { role ->
                Text(
                    role,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                detail.favoriteStatusLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            favoriteErrorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        DeviceDetailSection(title = "基本信息") {
            detail.ipAddress?.takeIf(String::isNotBlank)?.let { ToolResultRow("IPv4", it) }
            detail.macAddress?.takeIf(String::isNotBlank)?.let { ToolResultRow("MAC", it) }
            detail.vendor?.takeIf(String::isNotBlank)?.let { ToolResultRow("厂商", it) }
            detail.model?.takeIf(String::isNotBlank)?.let { ToolResultRow("型号", it) }
        }

        if (detail.hostname != null || detail.mdnsNames.isNotEmpty() || detail.upnpNames.isNotEmpty()) {
            DeviceDetailSection(title = "设备身份") {
                detail.hostname?.takeIf(String::isNotBlank)?.let { ToolResultRow("主机名", it) }
                if (detail.mdnsNames.isNotEmpty()) {
                    ToolResultRow("mDNS", detail.mdnsNames.joinToString("\n"))
                }
                if (detail.upnpNames.isNotEmpty()) {
                    ToolResultRow("UPnP", detail.upnpNames.joinToString("\n"))
                }
            }
        }

        DeviceDetailSection(title = "网络关系") {
            detail.role?.takeIf(String::isNotBlank)?.let { ToolResultRow("角色", it) }
            detail.networkScope?.takeIf(String::isNotBlank)?.let { ToolResultRow("范围", it) }
        }

        DeviceDetailSection(title = "观察状态") {
            ToolResultRow("本次扫描", if (detail.observedThisScan) "已发现" else "本次未发现")
            detail.lastSeenAt?.takeIf { it > 0L }?.let { timestamp ->
                ToolResultRow("最近发现", formatTimestamp(timestamp))
            }
        }
    }
}

@Composable
private fun DeviceDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            content = content,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    .format(Date(timestamp))
