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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenTcp: () -> Unit,
    onOpenSubnet: () -> Unit,
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
                Text("Tools", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "选择一个工具执行本地网络检测。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToolSection(
                title = "Connectivity",
                subtitle = "连通性检测",
                items = listOf(
                    ToolItem(Icons.Outlined.WifiTethering, "Ping", "测试目标是否可达", onOpenPing),
                    ToolItem(Icons.Outlined.Dns, "DNS Lookup", "检查域名解析", onOpenDns),
                    ToolItem(Icons.Outlined.Lan, "TCP Port Check", "检查服务端口", onOpenTcp),
                ),
            )
            ToolSection(
                title = "Network Utilities",
                subtitle = "网络计算工具",
                items = listOf(
                    ToolItem(Icons.Outlined.AccountTree, "IPv4 Subnet Calculator", "计算 IPv4 子网", onOpenSubnet),
                ),
            )
            ToolSection(
                title = "Diagnostics",
                subtitle = "检测结果与本地记录",
                items = listOf(
                    ToolItem(Icons.Outlined.Assessment, "Network Diagnostic Report", "生成检测参考报告", onOpenReport),
                    ToolItem(Icons.Outlined.History, "History", "查看本机历史记录", onOpenHistory),
                ),
            )
        }
    }
}

private data class ToolItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

@Composable
private fun ToolSection(
    title: String,
    subtitle: String,
    items: List<ToolItem>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    ToolCard(
                        icon = item.icon,
                        title = item.title,
                        description = item.description,
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
}
