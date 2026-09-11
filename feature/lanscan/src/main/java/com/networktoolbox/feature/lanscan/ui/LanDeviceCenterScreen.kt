package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.ToolScreenLazyLayout
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState

/**
 * Top-level LAN Device Center foundation.
 *
 * This screen intentionally has no range selector. Tools -> LAN Scanner keeps
 * the one-shot current/custom range workflow, while Devices scans only the
 * current IPv4 network through the same ViewModel and use case.
 */
@Composable
fun LanDeviceCenterScreen(
    uiState: LanScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRescan: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: List<FavoriteDevice> = emptyList(),
    onOpenDevice: (String) -> Unit = {},
) {
    val savedProfileEvidence = stringResource(R.string.lan_scan_saved_not_scanned)

    ToolScreenLazyLayout(modifier = modifier) {
        item {
            NetworkToolboxTopLevelHeader(
                title = "设备",
                description = null,
                onOpenMenu = onOpenMenu,
            )
        }

        when (val state = uiState) {
            LanScannerUiState.Idle -> item { DeviceCenterLoadingCard() }

            is LanScannerUiState.Ready -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.readiness.networkContext,
                        range = state.range,
                    )
                }
                item {
                    DeviceCenterReadyCard(
                        onStartScan = onStartScan,
                        notice = state.notice,
                    )
                }
                deviceCenterSavedProfileList(
                    favorites = favorites,
                    context = state.readiness.networkContext,
                    unseenEvidence = savedProfileEvidence,
                    onOpenDevice = onOpenDevice,
                )
            }

            is LanScannerUiState.Scanning -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.networkContext,
                        range = state.range,
                    )
                }
                item {
                    DeviceCenterScanningCard(state = state, onStopScan = onStopScan)
                }
                deviceCenterDeviceList(
                    devices = state.update.discoveredDevices,
                    favorites = favorites,
                    context = state.networkContext,
                    includeUnseenFavorites = false,
                    onOpenDevice = onOpenDevice,
                )
            }

            is LanScannerUiState.Completed -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.session.initialNetworkContext,
                        range = state.session.range,
                    )
                }
                item {
                    DeviceCenterSessionCard(
                        title = "扫描完成",
                        session = state.session,
                        showLimitedWarning = true,
                    )
                }
                item { DeviceCenterRescanButton(onRescan = onRescan) }
                deviceCenterDeviceList(
                    devices = state.session.discoveredDevices,
                    favorites = favorites,
                    context = state.session.initialNetworkContext,
                    onOpenDevice = onOpenDevice,
                )
            }

            is LanScannerUiState.Cancelled -> {
                item {
                    DeviceCenterNetworkSummaryCard(
                        context = state.session.initialNetworkContext,
                        range = state.session.range,
                    )
                }
                item {
                    DeviceCenterSessionCard(
                        title = "扫描已停止",
                        session = state.session,
                        showLimitedWarning = false,
                    )
                }
                item { DeviceCenterRescanButton(onRescan = onRescan) }
                deviceCenterDeviceList(
                    devices = state.session.discoveredDevices,
                    favorites = favorites,
                    context = state.session.initialNetworkContext,
                    onOpenDevice = onOpenDevice,
                )
            }

            is LanScannerUiState.NetworkChanged -> {
                item {
                    DeviceCenterMessageCard(
                        title = stringResource(R.string.lan_scan_network_changed_title),
                        message = stringResource(R.string.lan_scan_network_changed_message),
                    )
                }
            }

            is LanScannerUiState.UnsupportedNetwork -> {
                item { DeviceCenterNetworkSummaryCard(context = state.readiness.networkContext) }
                item {
                    DeviceCenterMessageCard(
                        title = if (state.readiness.networkContext.connectionType == ConnectionType.CELLULAR) {
                            "当前为移动网络"
                        } else {
                            "当前网络不可用"
                        },
                        message = state.message,
                    )
                }
            }

            is LanScannerUiState.VpnBlocked -> {
                item { DeviceCenterNetworkSummaryCard(context = state.readiness.networkContext) }
                item {
                    DeviceCenterMessageCard(
                        title = "当前检测到 VPN 网络",
                        message = state.message,
                    )
                }
            }

            is LanScannerUiState.Error -> {
                state.readiness?.let { readiness ->
                    val range = (readiness.rangeResult as? LanScanRangeResult.Ready)?.range
                    item {
                        DeviceCenterNetworkSummaryCard(
                            context = readiness.networkContext,
                            range = range,
                        )
                    }
                }
                item { DeviceCenterMessageCard(title = "扫描失败", message = state.message) }
                item { DeviceCenterRescanButton(onRescan = onRescan, label = "重试") }
            }
        }
    }
}

@Composable
private fun DeviceCenterNetworkSummaryCard(
    context: com.networktoolbox.core.network.model.NetworkContext,
    range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange? = null,
) {
    val summary = DeviceCenterPresentation.networkSummary(context, range)
    OutlinedNetworkCard {
        Text("当前网络", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.networkName ?: summary.networkLabel,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (summary.networkName != null) {
                    Text(
                        summary.networkLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                if (context.activeNetworkAvailable == false) "不可用" else "已连接",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        summary.subnet?.let { DetailRow(label = "网段", value = it) }
        summary.localAddress?.let { DetailRow(label = "本机", value = it) }
        summary.gateway?.let { DetailRow(label = "网关", value = it) }
        summary.wifiSignalLevel?.let { signal ->
            DetailRow(label = "信号", value = "$signal / 4")
        }
    }
}

@Composable
private fun DeviceCenterReadyCard(
    onStartScan: () -> Unit,
    notice: com.networktoolbox.feature.lanscan.presentation.LanScanNotice? = null,
) {
    notice?.let { currentNotice ->
        if (currentNotice == com.networktoolbox.feature.lanscan.presentation.LanScanNotice.NETWORK_CHANGED) {
            OutlinedNetworkCard(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(stringResource(R.string.lan_scan_network_changed_title))
                Text(
                    stringResource(R.string.lan_scan_network_changed_detail),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    OutlinedNetworkCard {
        Text(
            stringResource(R.string.lan_scan_not_scanned_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.lan_scan_not_scanned_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onStartScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始扫描")
        }
        Text(
            "扫描只在本机当前局域网中进行，结果保存在设备本地，不会上传。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DeviceCenterScanningCard(
    state: LanScannerUiState.Scanning,
    onStopScan: () -> Unit,
) {
    val update = state.update
    OutlinedNetworkCard {
        Text("正在扫描", style = MaterialTheme.typography.titleMedium)
        Text(state.range.displayLabel, style = NetworkToolboxTextStyles.TechnicalData)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${update.scannedHosts} / ${update.totalHosts}")
            Text(
                "已发现 ${update.discoveredDevices.size} 台设备",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = onStopScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("停止扫描")
        }
    }
}

@Composable
private fun DeviceCenterSessionCard(
    title: String,
    session: LanScanSession,
    showLimitedWarning: Boolean,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        session.range?.let { range ->
            Text(range.displayLabel, style = NetworkToolboxTextStyles.TechnicalData)
            if (showLimitedWarning && session.rangeWasLimited) {
                Text(
                    "当前网络范围较大，本次扫描已限制为当前 /24。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(LanScannerPresentation.sessionSummary(session))
    }
}

@Composable
private fun DeviceCenterRescanButton(
    onRescan: () -> Unit,
    label: String = "重新扫描",
) {
    OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

private fun LazyListScope.deviceCenterSavedProfileList(
    favorites: List<FavoriteDevice>,
    context: com.networktoolbox.core.network.model.NetworkContext,
    unseenEvidence: String,
    onOpenDevice: (String) -> Unit,
) {
    val items = DeviceCenterPresentation.savedProfilesBeforeScan(
        favorites = favorites,
        context = context,
        unseenEvidence = unseenEvidence,
    )
    if (items.isEmpty()) return

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.lan_scan_saved_devices_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                items.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    items(
        items = items,
        key = { it.detailKey },
    ) { deviceItem ->
        LanDeviceCard(
            presentation = deviceItem.card,
            onClick = { onOpenDevice(deviceItem.detailKey) },
        )
    }
}

@Composable
private fun DeviceCenterMessageCard(
    title: String,
    message: String,
) {
    OutlinedNetworkCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceCenterLoadingCard() {
    OutlinedNetworkCard {
        Text("正在读取网络状态", style = MaterialTheme.typography.titleMedium)
        Text("请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.deviceCenterDeviceList(
    devices: List<LanDevice>,
    favorites: List<FavoriteDevice>,
    context: com.networktoolbox.core.network.model.NetworkContext,
    includeUnseenFavorites: Boolean = true,
    onOpenDevice: (String) -> Unit,
) {
    val deviceItems = DeviceCenterPresentation.deviceList(
        devices = devices,
        favorites = favorites,
        context = context,
        includeUnseenFavorites = includeUnseenFavorites,
    )
    if (deviceItems.isEmpty()) {
        item {
            OutlinedNetworkCard {
                Text("未发现局域网设备", style = MaterialTheme.typography.titleMedium)
                Text(
                    "当前扫描范围内没有获得可确认的设备响应。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val observedCount = deviceItems.count { it.observedThisScan }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (observedCount > 0) "已发现设备" else "已保存设备",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                (if (observedCount > 0) observedCount else deviceItems.size).toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    items(
        items = deviceItems,
        key = { it.detailKey },
    ) { deviceItem ->
        LanDeviceCard(
            presentation = deviceItem.card,
            onClick = { onOpenDevice(deviceItem.detailKey) },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.MD),
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
