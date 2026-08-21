package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.model.ConnectionType

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenSubnet: () -> Unit,
    onOpenPing: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenTcp: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val networkContext = uiState.networkContext

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("NetworkToolbox", style = MaterialTheme.typography.headlineLarge)
            Text(
                "开源 Android 网络分析与故障排查辅助工具",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Network Status", "当前网络状态摘要")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoRow("网络类型", networkContext.connectionType.displayName())
                    InfoRow("IPv4", networkContext.ipv4Address.orUnknown())
                    InfoRow("IPv6", networkContext.ipv6Address.orUnknown())
                    InfoRow("网关", networkContext.gateway.orUnknown())
                    InfoRow("DNS", networkContext.dnsServers.joinToString().orUnknown())
                    InfoRow("VPN", networkContext.vpnStatus())

                    if (networkContext.connectionType == ConnectionType.WIFI ||
                        networkContext.wifiName != null ||
                        networkContext.wifiSignalLevel != null
                    ) {
                        InfoRow("Wi-Fi 名称", networkContext.wifiName.orUnknown())
                        InfoRow(
                            "信号级别",
                            networkContext.wifiSignalLevel?.let { "$it / 4" }.orUnknown(),
                        )
                    }
                }
            }

            SectionTitle("Quick Tools")
            ToolButton("Ping", onOpenPing)
            ToolButton("DNS Lookup", onOpenDns)
            ToolButton("TCP Port Check", onOpenTcp)

            SectionTitle("Network Utilities")
            ToolButton("IPv4 Subnet Calculator", onOpenSubnet)

            SectionTitle("Diagnostics")
            ToolButton("Network Diagnostic Report", onOpenReport)

            SectionTitle("History")
            ToolButton("History", onOpenHistory)
        }
    }
}

@Composable
private fun SectionTitle(
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
private fun ToolButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(label)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(0.35f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            modifier = Modifier.weight(0.65f),
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "未知"

private fun com.networktoolbox.core.network.model.NetworkContext.vpnStatus(): String = when {
    vpnActive == true -> "已启用"
    vpnActive == false -> "未启用"
    else -> "未知"
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知"
}
