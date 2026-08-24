package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecentHistoryPreview(
    val type: String,
    val title: String,
    val summary: String,
    val timestamp: Long,
)

@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    recentHistory: RecentHistoryPreview?,
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NetworkToolbox", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "了解网络状态，快速执行本地检测。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NetworkStatusCard(uiState.networkContext)

            SectionHeader(
                title = "Quick Actions",
                subtitle = "常用检测工具",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolCard(
                    icon = Icons.Outlined.WifiTethering,
                    title = "Ping",
                    description = "测试连通性",
                    onClick = onOpenPing,
                    modifier = Modifier.weight(1f),
                )
                ToolCard(
                    icon = Icons.Outlined.Dns,
                    title = "DNS",
                    description = "检查域名解析",
                    onClick = onOpenDns,
                    modifier = Modifier.weight(1f),
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Network Diagnostic",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "一键检测网络状态",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Button(onClick = onOpenReport) {
                        Text("Run Check")
                    }
                }
            }

            SectionHeader(title = "Recent Diagnostic")
            RecentDiagnosticCard(
                recentHistory = recentHistory,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
internal fun NetworkStatusCard(context: NetworkContext) {
    val connectionType = context.connectionType.displayName()
    val connectionStatus = context.connectionStatus()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(connectionType, style = MaterialTheme.typography.titleLarge)
                    Text(
                        connectionStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        context.ipv4Address.orUnknown(),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusMetric(
                    label = "Gateway",
                    value = context.gateway.orUnknown(),
                    modifier = Modifier.weight(1f),
                )
                StatusMetric(
                    label = "DNS",
                    value = context.dnsServers.firstOrNull().orUnknown(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusMetric(
                    label = "IPv6",
                    value = context.ipv6Address.orUnknown(),
                    modifier = Modifier.weight(1f),
                )
                StatusMetric(
                    label = "Signal",
                    value = context.wifiSignalLevel?.let { "$it/4" }.orUnknown(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 132.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentDiagnosticCard(
    recentHistory: RecentHistoryPreview?,
    onOpenHistory: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Recent Diagnostic", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onOpenHistory) {
                    Text("View History >")
                }
            }
            if (recentHistory == null) {
                Text(
                    "No recent checks yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Last check · ${recentHistory.timestamp.toRecentTime()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(recentHistory.type, style = MaterialTheme.typography.titleMedium)
                Text(recentHistory.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    recentHistory.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun NetworkContext.connectionStatus(): String = when {
    connectionType == ConnectionType.UNKNOWN -> "Unknown"
    ipv4Address != null || ipv6Address != null -> "Connected"
    else -> "Network available"
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "Mobile network"
    ConnectionType.ETHERNET -> "Ethernet"
    ConnectionType.BLUETOOTH -> "Bluetooth"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "Unknown network"
}

private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "Unknown"

private fun Long.toRecentTime(): String {
    val elapsedMinutes = ((System.currentTimeMillis() - this).coerceAtLeast(0L)) / 60_000L
    return when {
        elapsedMinutes < 1 -> "Just now"
        elapsedMinutes < 60 -> "$elapsedMinutes min ago"
        elapsedMinutes < 1_440 -> "${elapsedMinutes / 60} hr ago"
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
    }
}
