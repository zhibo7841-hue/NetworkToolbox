package com.networktoolbox.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.networktoolbox.core.designsystem.NetworkToolAccent

internal enum class DashboardToolId {
    PING,
    DNS,
    TCP,
    TRACEROUTE,
    SUBNET,
    LAN_SCAN,
    REPORT,
    HISTORY,
}

internal data class DashboardNavigationCallbacks(
    val onOpenPing: () -> Unit,
    val onOpenDns: () -> Unit,
    val onOpenTcp: () -> Unit,
    val onOpenTraceroute: () -> Unit,
    val onOpenSubnet: () -> Unit,
    val onOpenLanScan: () -> Unit,
    val onOpenReport: () -> Unit,
    val onOpenHistory: () -> Unit,
)

internal data class DashboardToolDefinition(
    val id: DashboardToolId,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accent: NetworkToolAccent,
    val onClick: () -> Unit,
)

internal data class DashboardToolSection(
    val title: String,
    val subtitle: String,
    val tools: List<DashboardToolDefinition>,
)

internal fun dashboardToolDefinitions(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolDefinition> = listOf(
    DashboardToolDefinition(
        id = DashboardToolId.PING,
        icon = Icons.Outlined.WifiTethering,
        title = "Ping",
        description = "测试主机连通性",
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenPing,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.DNS,
        icon = Icons.Outlined.Dns,
        title = "DNS Lookup",
        description = "查询域名解析",
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenDns,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TCP,
        icon = Icons.Outlined.Lan,
        title = "TCP Port Check",
        description = "检查服务端口",
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenTcp,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TRACEROUTE,
        icon = Icons.Outlined.AccountTree,
        title = "Traceroute",
        description = "追踪网络路径",
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenTraceroute,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.SUBNET,
        icon = Icons.Outlined.AccountTree,
        title = "IPv4 子网计算",
        description = "计算网络地址",
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenSubnet,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.LAN_SCAN,
        icon = Icons.Outlined.Lan,
        title = "局域网扫描",
        description = "发现局域网设备",
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenLanScan,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.REPORT,
        icon = Icons.Outlined.Assessment,
        title = "网络诊断",
        description = "生成诊断参考报告",
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenReport,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.HISTORY,
        icon = Icons.Outlined.History,
        title = "历史记录",
        description = "查看本机检测记录",
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenHistory,
    ),
)

internal fun quickToolDefinitions(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolDefinition> = dashboardToolDefinitions(callbacks).filter { definition ->
    definition.id in setOf(
        DashboardToolId.PING,
        DashboardToolId.DNS,
        DashboardToolId.TRACEROUTE,
        DashboardToolId.LAN_SCAN,
    )
}

internal fun dashboardToolSections(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolSection> {
    val definitions = dashboardToolDefinitions(callbacks).associateBy { it.id }
    return listOf(
        DashboardToolSection(
            title = "连通性检测",
            subtitle = "测试网络连接和服务响应",
            tools = listOf(
                definitions.getValue(DashboardToolId.PING),
                definitions.getValue(DashboardToolId.DNS),
                definitions.getValue(DashboardToolId.TCP),
                definitions.getValue(DashboardToolId.TRACEROUTE),
            ),
        ),
        DashboardToolSection(
            title = "网络工具",
            subtitle = "计算和分析网络地址",
            tools = listOf(
                definitions.getValue(DashboardToolId.SUBNET),
                definitions.getValue(DashboardToolId.LAN_SCAN),
            ),
        ),
        DashboardToolSection(
            title = "诊断与记录",
            subtitle = "生成参考报告并查看本地记录",
            tools = listOf(
                definitions.getValue(DashboardToolId.REPORT),
                definitions.getValue(DashboardToolId.HISTORY),
            ),
        ),
    )
}
