package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing

@Composable
fun ToolsScreen(
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenTcp: () -> Unit,
    onOpenTraceroute: () -> Unit,
    onOpenSubnet: () -> Unit,
    onOpenLanScan: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callbacks = DashboardNavigationCallbacks(
        onOpenPing = onOpenPing,
        onOpenDns = onOpenDns,
        onOpenTcp = onOpenTcp,
        onOpenTraceroute = onOpenTraceroute,
        onOpenSubnet = onOpenSubnet,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("工具", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "选择一个工具执行本地网络检测。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            dashboardToolSections(callbacks).forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                    SectionHeader(
                        title = section.title,
                        subtitle = section.subtitle,
                    )
                    DashboardToolGrid(section.tools)
                }
            }
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
                ToolCard(
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
