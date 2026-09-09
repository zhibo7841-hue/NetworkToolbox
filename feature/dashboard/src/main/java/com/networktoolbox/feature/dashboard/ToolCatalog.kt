package com.networktoolbox.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dns
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
}

internal data class DashboardNavigationCallbacks(
    val onOpenPing: () -> Unit,
    val onOpenDns: () -> Unit,
    val onOpenTcp: () -> Unit,
    val onOpenTraceroute: () -> Unit,
    val onOpenSubnet: () -> Unit,
    val onOpenLanScan: () -> Unit,
    val onOpenReport: () -> Unit,
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
    val subtitle: String? = null,
    val tools: List<DashboardToolDefinition>,
)

internal fun dashboardToolDefinitions(
    callbacks: DashboardNavigationCallbacks,
): List<DashboardToolDefinition> = listOf(
    DashboardToolDefinition(
        id = DashboardToolId.PING,
        icon = Icons.Outlined.WifiTethering,
        title = "Ping",
        description = "测试目标连通性",
        accent = NetworkToolAccent.PRIMARY,
        onClick = callbacks.onOpenPing,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.DNS,
        icon = Icons.Outlined.Dns,
        title = "DNS 查询",
        description = "查询域名解析",
        accent = NetworkToolAccent.CYAN,
        onClick = callbacks.onOpenDns,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TCP,
        icon = Icons.Outlined.Lan,
        title = "TCP 端口检测",
        description = "检查 TCP 服务端口",
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenTcp,
    ),
    DashboardToolDefinition(
        id = DashboardToolId.TRACEROUTE,
        icon = Icons.Outlined.AccountTree,
        title = "Traceroute",
        description = "追踪目标网络路径",
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
        description = "自动检查网络问题",
        accent = NetworkToolAccent.AMBER,
        onClick = callbacks.onOpenReport,
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
            title = "连通与路径",
            tools = listOf(
                definitions.getValue(DashboardToolId.PING),
                definitions.getValue(DashboardToolId.TCP),
                definitions.getValue(DashboardToolId.TRACEROUTE),
            ),
        ),
        DashboardToolSection(
            title = "解析与服务",
            tools = listOf(
                definitions.getValue(DashboardToolId.DNS),
            ),
        ),
        DashboardToolSection(
            title = "网络与地址",
            tools = listOf(
                definitions.getValue(DashboardToolId.SUBNET),
                definitions.getValue(DashboardToolId.LAN_SCAN),
            ),
        ),
        DashboardToolSection(
            title = "诊断",
            tools = listOf(
                definitions.getValue(DashboardToolId.REPORT),
            ),
        ),
    )
}
