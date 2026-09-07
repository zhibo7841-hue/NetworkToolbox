package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkCard
import com.networktoolbox.core.designsystem.NetworkStatusChip
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.ToolIconContainer
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
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
    onOpenTraceroute: () -> Unit = {},
    onOpenLanScan: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val callbacks = DashboardNavigationCallbacks(
        onOpenPing = onOpenPing,
        onOpenDns = onOpenDns,
        onOpenTcp = {},
        onOpenTraceroute = onOpenTraceroute,
        onOpenSubnet = {},
        onOpenLanScan = onOpenLanScan,
        onOpenReport = onOpenReport,
        onOpenHistory = onOpenHistory,
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = NetworkToolboxSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.LG),
        ) {
            DashboardBrandHeader()

            NetworkSummaryCard(uiState.networkContext)

            NetworkCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
                    ) {
                        ToolIconContainer(
                            icon = Icons.Outlined.Assessment,
                            accent = NetworkToolAccent.PRIMARY,
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "自动诊断",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "自动检查当前网络环境",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "仅在本机执行，不上传诊断数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    PrimaryActionButton(
                        onClick = onOpenReport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开始网络诊断")
                    }
                }
            }

            SectionHeader(
                title = "快速工具",
                subtitle = "常用网络检测",
            )
            DashboardToolGrid(quickToolDefinitions(callbacks))

            SectionHeader(title = "最近诊断")
            RecentDiagnosticCard(
                recentHistory = recentHistory,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
private fun DashboardToolGrid(items: List<DashboardToolDefinition>) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            rowItems.forEach { item ->
                QuickToolCard(
                    icon = item.icon,
                    title = item.title,
                    description = item.description,
                    accent = item.accent,
                    onClick = item.onClick,
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun NetworkSummaryCard(context: NetworkContext) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val ipv6Addresses = NetworkStatusPresentation.ipv6Addresses(context)
    val ipv6Status = NetworkStatusPresentation.ipv6Status(ipv6Addresses)
    val showGateway = NetworkStatusPresentation.shouldShowGateway(context)
    val showWifiSignal = NetworkStatusPresentation.shouldShowWifiSignal(context)
    val gateway = context.gateway?.takeIf(String::isNotBlank)
    val dnsServers = context.dnsServers
        .filter(String::isNotBlank)
        .distinct()
    val ipv4Address = context.ipv4Address?.takeIf(String::isNotBlank) ?: "未配置"
    val ipv6Label = NetworkStatusPresentation.ipv6Label(ipv6Status)
    val connectionStatus = NetworkStatusPresentation.connectionStatusLabel(context)
    val statusState = NetworkStatusPresentation.connectionStatusVisualState(context)

    NetworkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    NetworkStatusPresentation.networkIdentity(context),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (context.vpnActive == true) "当前通过 VPN 网络" else "当前网络连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NetworkStatusChip(
                status = statusState,
                label = connectionStatus,
            )
        }

        if (context.activeNetworkAvailable == false) {
            Text(
                "请连接 Wi-Fi 或移动网络后重试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SummaryMetric(
            label = "IPv4 地址",
            value = ipv4Address,
            modifier = Modifier.fillMaxWidth(),
            technical = context.ipv4Address?.isNotBlank() == true,
        )

        if (showGateway) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                SummaryMetric(
                    label = "网关",
                    value = gateway ?: "未获得",
                    modifier = Modifier.weight(1f),
                    technical = gateway != null,
                )
                SummaryMetric(
                    label = "DNS",
                    value = NetworkStatusPresentation.dnsSummary(dnsServers),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            SummaryMetric(
                label = "DNS",
                value = NetworkStatusPresentation.dnsSummary(dnsServers),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showWifiSignal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
            ) {
                SummaryMetric(
                    label = "IPv6",
                    value = ipv6Label,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "信号",
                    value = context.wifiSignalLevel?.let { "$it / 4" } ?: "未获得",
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            SummaryMetric(
                label = "IPv6",
                value = ipv6Label,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "收起详情" else "查看详情 >")
            }
        }

        if (showDetails) {
            HorizontalDivider()

            DetailSection("网络信息") {
                context.wifiName
                    ?.let(NetworkStatusPresentation::displayableWifiName)
                    ?.let { DetailRow("网络名称", it) }
                DetailRow("网络类型", context.connectionType.displayName())
                context.interfaceName?.let { DetailRow("接口", it) }
                DetailRow(
                    "IPv4 地址",
                    context.ipv4Address?.takeIf(String::isNotBlank) ?: "未配置",
                    technical = context.ipv4Address?.isNotBlank() == true,
                )
                context.ipv4PrefixLength?.let { prefix ->
                    if (context.ipv4Address?.isNotBlank() == true) {
                        DetailRow("IPv4 前缀", "/$prefix", technical = true)
                        NetworkStatusPresentation.ipv4PrefixToNetmask(prefix)?.let { mask ->
                            DetailRow("子网掩码", mask, technical = true)
                        }
                    }
                }
                DetailRow("IPv6 状态", ipv6Label)
                if (ipv6Addresses.isNotEmpty()) {
                    Text(
                        "IPv6 地址",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                        ipv6Addresses.forEach { address ->
                            Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                        }
                    }
                }
            }

            if (showGateway) {
                DetailSection("路由") {
                    DetailRow("IPv4 网关", gateway ?: "未获得", technical = gateway != null)
                }
            }

            DetailSection("DNS") {
                Text(
                    "网络配置 DNS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dnsServers.isEmpty()) {
                    Text("未配置", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.XS)) {
                        dnsServers.forEach { address ->
                            Text(address, style = NetworkToolboxTextStyles.TechnicalData)
                        }
                    }
                }
                context.privateDnsActive?.let { active ->
                    DetailRow("私人 DNS", if (active) "已启用" else "未启用")
                }
                context.privateDnsServerName?.let { name ->
                    DetailRow("私人 DNS 名称", name)
                }
            }

            DetailSection("连接状态") {
                DetailRow("VPN", context.vpnActive.vpnDisplayName())
                DetailRow("系统联网验证", context.validated.validationDisplayName())
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    technical: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(0.4f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(0.6f),
            text = value,
            style = if (technical) {
                NetworkToolboxTextStyles.TechnicalData
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun RecentDiagnosticCard(
    recentHistory: RecentHistoryPreview?,
    onOpenHistory: () -> Unit,
) {
    NetworkCard(
        modifier = Modifier.clickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onOpenHistory,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            ToolIconContainer(
                icon = Icons.Outlined.Assessment,
                accent = NetworkToolAccent.AMBER,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("最近诊断", style = MaterialTheme.typography.titleMedium)
                Text(
                    HomePresentation.recentDiagnosticBody(recentHistory),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (recentHistory == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (recentHistory != null) {
                    Text(
                        recentHistory.timestamp.toRecentTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HomePresentation.recentDiagnosticSummary(recentHistory)?.let { summary ->
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知网络"
}

private fun Boolean?.vpnDisplayName(): String = when (this) {
    true -> "已启用"
    false -> "未启用"
    null -> "未知"
}

private fun Boolean?.validationDisplayName(): String = when (this) {
    true -> "已通过"
    false -> "未通过"
    null -> "未知"
}

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
