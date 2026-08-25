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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
                Text("NetworkToolbox", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "快速了解当前网络状态",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NetworkStatusCard(uiState.networkContext)

            SectionHeader(
                title = "快速操作",
                subtitle = "常用检测工具",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolCard(
                    icon = Icons.Outlined.WifiTethering,
                    title = "Ping",
                    description = "测试网络连通性",
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
                        .padding(16.dp),
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
                            "网络诊断",
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
                        Text("开始检测")
                    }
                }
            }

            SectionHeader(title = "最近诊断")
            RecentDiagnosticCard(
                recentHistory = recentHistory,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
internal fun NetworkStatusCard(context: NetworkContext) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "收起详情" else "网络详情 >")
                }
            }

            if (showDetails) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusMetric(
                        label = "网关",
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
                        label = "信号",
                        value = context.wifiSignalLevel?.let { "$it/4" }.orUnknown(),
                        modifier = Modifier.weight(1f),
                    )
                }
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
                    Text("最近诊断", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onOpenHistory) {
                    Text("查看历史 >")
                }
            }
            if (recentHistory == null) {
                Text(
                    "暂无诊断记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "最近检查 · ${recentHistory.timestamp.toRecentTime()}",
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
    connectionType == ConnectionType.UNKNOWN -> "未知"
    ipv4Address != null || ipv6Address != null -> "已连接"
    else -> "网络可用"
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知网络"
}

private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "未知"

private fun Long.toRecentTime(): String {
    val elapsedMinutes = ((System.currentTimeMillis() - this).coerceAtLeast(0L)) / 60_000L
    return when {
        elapsedMinutes < 1 -> "刚刚"
        elapsedMinutes < 60 -> "$elapsedMinutes 分钟前"
        elapsedMinutes < 1_440 -> "${elapsedMinutes / 60} 小时前"
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
    }
}
