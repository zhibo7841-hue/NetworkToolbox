package com.networktoolbox.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
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
    modifier: Modifier = Modifier,
) {
    val networkContext = uiState.networkContext

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Network Toolbox", style = MaterialTheme.typography.headlineMedium)
            Text("当前网络", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()

            InfoRow("网络类型", networkContext.connectionType.displayName())
            InfoRow("IPv4", networkContext.ipv4Address.orUnknown())
            InfoRow("IPv6", networkContext.ipv6Address.orUnknown())
            InfoRow("网关", networkContext.gateway.orUnknown())
            InfoRow("DNS", networkContext.dnsServers.joinToString().orUnknown())
            InfoRow(
                "VPN",
                when (networkContext.vpnActive) {
                    true -> "已启用"
                    false -> "未启用"
                    null -> "未知"
                },
            )

            if (networkContext.connectionType == ConnectionType.WIFI ||
                networkContext.wifiName != null ||
                networkContext.wifiSignalLevel != null
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
                InfoRow("网络名称", networkContext.wifiName.orUnknown())
                InfoRow(
                    "信号级别",
                    networkContext.wifiSignalLevel?.let { "$it / 4" }.orUnknown(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSubnet) {
                Text("IPv4 子网计算器")
            }
            Button(onClick = onOpenPing) {
                Text("Ping")
            }
            Button(onClick = onOpenDns) {
                Text("DNS Lookup")
            }
            Button(onClick = onOpenTcp) {
                Text("TCP Port Check")
            }
        }
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

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知"
}
