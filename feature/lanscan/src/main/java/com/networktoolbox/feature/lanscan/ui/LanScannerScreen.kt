package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState

@Composable
fun LanScannerScreen(
    uiState: LanScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = onStartScan,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("返回工具")
            }
            Text("局域网扫描", style = MaterialTheme.typography.headlineMedium)
            Text(
                "扫描当前局域网中的在线设备。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val state = uiState) {
                LanScannerUiState.Idle -> LoadingCard()
                is LanScannerUiState.Ready -> ReadyContent(
                    context = state.readiness.networkContext,
                    range = state.range,
                    onStartScan = onStartScan,
                )

                is LanScannerUiState.Scanning -> ScanningContent(
                    state = state,
                    onStopScan = onStopScan,
                )

                is LanScannerUiState.Completed -> SessionContent(
                    title = "扫描完成",
                    session = state.session,
                    actionLabel = "重新扫描",
                    onAction = onRetry,
                )

                is LanScannerUiState.Cancelled -> SessionContent(
                    title = "扫描已停止",
                    session = state.session,
                    actionLabel = "重新扫描",
                    onAction = onRetry,
                )

                is LanScannerUiState.NetworkChanged -> NetworkChangedContent(
                    session = state.session,
                    onRetry = onRetry,
                )

                is LanScannerUiState.UnsupportedNetwork -> UnsupportedContent(
                    context = state.readiness.networkContext,
                )

                is LanScannerUiState.VpnBlocked -> VpnBlockedContent()
                is LanScannerUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    context: NetworkContext,
    range: LanScanRange,
    onStartScan: () -> Unit,
) {
    NetworkSummaryCard(context = context, range = range)

    if (range.rangeWasLimited) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("当前网络范围较大", style = MaterialTheme.typography.titleMedium)
                Text(
                    "为避免大量网络探测，本次扫描范围已限制为当前 /24。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("扫描范围：${range.cidr}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    PrivacyHint()
    Button(
        onClick = onStartScan,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("开始扫描")
    }
}

@Composable
private fun NetworkSummaryCard(
    context: NetworkContext,
    range: LanScanRange,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("当前网络", style = MaterialTheme.typography.titleMedium)
            Text(
                "${context.connectionType.displayName()} · ${range.cidr}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${range.hostCount} 个可扫描地址",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            context.ipv4Address?.takeIf(String::isNotBlank)?.let {
                DetailRow("本机", it)
            }
            DetailRow("网关", context.gateway?.takeIf(String::isNotBlank) ?: "未确认")
        }
    }
}

@Composable
private fun ScanningContent(
    state: LanScannerUiState.Scanning,
    onStopScan: () -> Unit,
) {
    val update = state.update
    StatusCard(
        title = "正在扫描",
        content = {
            Text(
                "范围：${state.range.cidr}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${update.scannedHosts} / ${update.totalHosts}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("已发现 ${update.discoveredDevices.size} 台设备")
            LinearProgressIndicator(
                progress = {
                    LanScannerPresentation.progressFraction(
                        scannedHosts = update.scannedHosts,
                        totalHosts = update.totalHosts,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            update.elapsedMs?.let { elapsed ->
                Text(
                    "已用时 ${LanScannerPresentation.elapsedText(elapsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    OutlinedButton(
        onClick = onStopScan,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("停止扫描")
    }
    DeviceList(update.discoveredDevices)
}

@Composable
private fun SessionContent(
    title: String,
    session: LanScanSession,
    actionLabel: String,
    onAction: () -> Unit,
) {
    StatusCard(
        title = title,
        content = {
            session.range?.let { range ->
                Text(range.cidr, style = MaterialTheme.typography.titleMedium)
                if (session.rangeWasLimited) {
                    Text(
                        "原始范围 ${range.originalCidr}，本次已限制为当前 /24。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (session.status == LanScanStatus.COMPLETED) {
                Text(LanScannerPresentation.sessionSummary(session))
            } else {
                Text(
                    "已扫描 ${session.scannedHosts} / ${session.totalHosts} 个地址 · " +
                        "发现 ${session.discoveredDevices.size} 台设备 · " +
                        LanScannerPresentation.elapsedText(session.elapsedMs),
                )
            }
        },
    )
    OutlinedButton(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(actionLabel)
    }
    DeviceList(session.discoveredDevices)
}

@Composable
private fun NetworkChangedContent(
    session: LanScanSession,
    onRetry: () -> Unit,
) {
    StatusCard(
        title = "网络已发生变化",
        content = {
            Text("扫描已停止，以避免混合不同局域网的结果。")
            Text(
                "请在网络稳定后重新扫描。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("已扫描 ${session.scannedHosts} / ${session.totalHosts} 个地址")
        },
    )
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重新扫描")
    }
    DeviceList(session.discoveredDevices)
}

@Composable
private fun UnsupportedContent(
    context: NetworkContext,
) {
    StatusCard(
        title = if (context.connectionType == ConnectionType.CELLULAR) {
            "当前为移动网络"
        } else {
            "当前网络不可用"
        },
        content = {
            Text("LAN Scanner 用于扫描 Wi-Fi 或以太网局域网。")
        },
    )
}

@Composable
private fun VpnBlockedContent() {
    StatusCard(
        title = "当前检测到 VPN 网络",
        content = {
            Text("第一版 LAN Scanner 暂不在 VPN 网络下自动扫描，以避免扫描错误的虚拟网段。")
        },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    StatusCard(title = "扫描失败", content = { Text(message) })
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重试")
    }
}

@Composable
private fun LoadingCard() {
    StatusCard(title = "正在读取网络状态", content = { Text("请稍候…") })
}

@Composable
private fun PrivacyHint() {
    Text(
        "扫描只在当前本地网络中进行，结果保存在设备本地，不会上传。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeviceList(devices: List<LanDevice>) {
    if (devices.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已发现设备", style = MaterialTheme.typography.titleMedium)
            Text(
                devices.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        devices.forEach { device -> DeviceCard(device) }
    }
}

@Composable
private fun DeviceCard(device: LanDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(device.ipAddress, style = MaterialTheme.typography.titleMedium)
                LanScannerPresentation.deviceRole(device)
                    .takeIf(String::isNotBlank)
                    ?.let { role ->
                        Text(
                            role,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
            }
            LanScannerPresentation.deviceSecondaryText(device)?.let { secondary ->
                Text(
                    secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            device.hostName?.takeIf(String::isNotBlank)?.let { name ->
                Text(
                    "名称 · $name",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            device.macAddress?.takeIf(String::isNotBlank)?.let { mac ->
                Text(
                    "MAC · $mac",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.35f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.VPN -> "VPN"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.UNKNOWN -> "未知网络"
}
