package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.feature.lanscan.R
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.presentation.DeviceCenterPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScanRangeMode
import com.networktoolbox.feature.lanscan.presentation.LanScannerPresentation
import com.networktoolbox.feature.lanscan.presentation.LanScannerUiState

@Composable
fun LanScannerScreen(
    uiState: LanScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onStartScan,
    onModifyRange: () -> Unit = {},
    onRangeModeChanged: (LanScanRangeMode) -> Unit = {},
    onCustomStartAddressChanged: (String) -> Unit = {},
    onCustomEndAddressChanged: (String) -> Unit = {},
    savedProfiles: List<FavoriteDevice> = emptyList(),
    isTopLevelDestination: Boolean = false,
    onOpenMenu: () -> Unit = {},
    scrollState: ScrollState? = null,
) {
    ToolScreenLayout(
        modifier = modifier,
        scrollState = scrollState,
    ) {
        if (isTopLevelDestination) {
            NetworkToolboxTopLevelHeader(
                title = "设备",
                description = "发现并查看局域网设备。",
                onOpenMenu = onOpenMenu,
            )
        } else {
            ToolScreenHeader(
                title = "局域网扫描",
                description = null,
                icon = Icons.Outlined.Lan,
                accent = NetworkToolAccent.PRIMARY,
                onBack = onBack,
            )
        }

        when (val state = uiState) {
            LanScannerUiState.Idle -> LoadingCard()
            is LanScannerUiState.Ready -> ReadyContent(
                context = state.readiness.networkContext,
                range = state.range,
                rangeMode = state.rangeMode,
                customStartAddress = state.customStartAddress,
                customEndAddress = state.customEndAddress,
                customRangeResult = state.customRangeResult,
                onStartScan = onStartScan,
                onRangeModeChanged = onRangeModeChanged,
                onCustomStartAddressChanged = onCustomStartAddressChanged,
                onCustomEndAddressChanged = onCustomEndAddressChanged,
                notice = state.notice,
            )

            is LanScannerUiState.Scanning -> ScanningContent(
                state = state,
                onStopScan = onStopScan,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Completed -> SessionContent(
                session = state.session,
                onRescan = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Cancelled -> SessionContent(
                session = state.session,
                onRescan = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.NetworkChanged -> NetworkChangedContent(
                onModifyRange = onModifyRange,
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

@Composable
private fun ReadyContent(
    context: NetworkContext,
    range: LanScanRange,
    rangeMode: LanScanRangeMode,
    customStartAddress: String,
    customEndAddress: String,
    customRangeResult: LanCustomRangeResult,
    onStartScan: () -> Unit,
    onRangeModeChanged: (LanScanRangeMode) -> Unit,
    onCustomStartAddressChanged: (String) -> Unit,
    onCustomEndAddressChanged: (String) -> Unit,
    notice: com.networktoolbox.feature.lanscan.presentation.LanScanNotice?,
) {
    RangeModeSelector(
        selectedMode = rangeMode,
        onModeChanged = onRangeModeChanged,
    )

    if (rangeMode == LanScanRangeMode.CURRENT_NETWORK) {
        NetworkSummaryCard(context = context, range = range)
    } else {
        CustomRangeCard(
            context = context,
            startAddress = customStartAddress,
            endAddress = customEndAddress,
            result = customRangeResult,
            onStartAddressChanged = onCustomStartAddressChanged,
            onEndAddressChanged = onCustomEndAddressChanged,
        )
    }

    if (rangeMode == LanScanRangeMode.CURRENT_NETWORK && range.rangeWasLimited) {
        OutlinedNetworkCard(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text("当前网络范围较大", style = MaterialTheme.typography.titleMedium)
            Text(
                "为避免大量网络探测，本次扫描范围已限制为当前 /24。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("扫描范围：${range.displayLabel}", style = MaterialTheme.typography.bodyMedium)
        }
    }

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

    LanScanStartCard(
        onStartScan = onStartScan,
        enabled = rangeMode == LanScanRangeMode.CURRENT_NETWORK ||
            customRangeResult is LanCustomRangeResult.Valid,
    )
}

@Composable
private fun RangeModeSelector(
    selectedMode: LanScanRangeMode,
    onModeChanged: (LanScanRangeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text("扫描范围", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM),
        ) {
            if (selectedMode == LanScanRangeMode.CURRENT_NETWORK) {
                PrimaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CURRENT_NETWORK) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("当前网络")
                }
                SecondaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CUSTOM) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("自定义范围")
                }
            } else {
                SecondaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CURRENT_NETWORK) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("当前网络")
                }
                PrimaryActionButton(
                    onClick = { onModeChanged(LanScanRangeMode.CUSTOM) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("自定义范围")
                }
            }
        }
    }
}

@Composable
private fun CustomRangeCard(
    context: NetworkContext,
    startAddress: String,
    endAddress: String,
    result: LanCustomRangeResult,
    onStartAddressChanged: (String) -> Unit,
    onEndAddressChanged: (String) -> Unit,
) {
    ToolInputSection(title = "${context.connectionType.displayName()} · 自定义 IPv4") {
        androidx.compose.material3.OutlinedTextField(
            value = startAddress,
            onValueChange = onStartAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("起始 IP") },
            placeholder = { Text("10.0.1.1") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            isError = result is LanCustomRangeResult.Invalid &&
                result.reason == com.networktoolbox.feature.lanscan.domain.LanCustomRangeError.INVALID_START,
        )
        androidx.compose.material3.OutlinedTextField(
            value = endAddress,
            onValueChange = onEndAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("结束 IP") },
            placeholder = { Text("10.0.1.254") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            isError = result is LanCustomRangeResult.Invalid &&
                result.reason == com.networktoolbox.feature.lanscan.domain.LanCustomRangeError.INVALID_END,
        )
        when (result) {
            LanCustomRangeResult.Incomplete -> Unit
            is LanCustomRangeResult.Invalid -> Text(
                result.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            is LanCustomRangeResult.Valid -> Text(
                "${result.range.hostCount} 个地址",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NetworkSummaryCard(
    context: NetworkContext,
    range: LanScanRange,
) {
    ToolInputSection(title = "当前网络") {
        Text(
            "${context.connectionType.displayName()} · ${range.displayLabel}",
            style = NetworkToolboxTextStyles.TechnicalData,
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

@Composable
private fun ScanningContent(
    state: LanScannerUiState.Scanning,
    onStopScan: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    val update = state.update
    LanScanRunningCard(
        rangeLabel = state.range.displayLabel,
        update = update,
        onStopScan = onStopScan,
    )
    DeviceList(
        devices = update.discoveredDevices,
        context = state.networkContext,
        savedProfiles = savedProfiles,
    )
}

@Composable
private fun SessionContent(
    session: LanScanSession,
    onRescan: () -> Unit,
    onModifyRange: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    LanScanSessionSummaryCard(session = session)
    LanScanRescanButton(onRescan = onRescan)
    TextButton(onClick = onModifyRange) {
        Text("修改扫描范围 >")
    }
    DeviceList(
        devices = session.discoveredDevices,
        context = session.initialNetworkContext,
        savedProfiles = savedProfiles,
    )
}

@Composable
private fun NetworkChangedContent(
    onModifyRange: () -> Unit,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = stringResource(R.string.lan_scan_network_changed_title),
            status = StatusVisualState.NOTICE,
            label = "网络变化",
        )
        Text(
            stringResource(R.string.lan_scan_network_changed_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(onClick = onModifyRange) {
        Text("重新设置")
    }
}

@Composable
private fun UnsupportedContent(
    context: NetworkContext,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = if (context.connectionType == ConnectionType.CELLULAR) {
                "当前为移动网络"
            } else {
                "当前网络不可用"
            },
            status = StatusVisualState.NOT_EXECUTED,
            label = "未执行",
        )
        Text("局域网扫描用于扫描 Wi-Fi 或以太网局域网。")
    }
}

@Composable
private fun VpnBlockedContent() {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "当前检测到 VPN 网络",
            status = StatusVisualState.NOT_EXECUTED,
            label = "未执行",
        )
        Text("第一版局域网扫描暂不在 VPN 网络下自动扫描，以避免扫描错误的虚拟网段。")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    LanScanFailureSection(
        message = message,
        onRetry = onRetry,
    )
}

@Composable
private fun LoadingCard() {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "正在读取网络状态",
            status = StatusVisualState.RUNNING,
            label = "读取中",
        )
        Text("请稍候…")
    }
}

@Composable
private fun DeviceList(
    devices: List<LanDevice>,
    context: NetworkContext,
    savedProfiles: List<FavoriteDevice>,
) {
    val deviceItems = DeviceCenterPresentation.deviceList(
        devices = devices,
        favorites = savedProfiles,
        context = context,
        includeUnseenFavorites = false,
    )
    if (deviceItems.isEmpty()) return

    val observedCount = deviceItems.count { it.observedThisScan }
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.lan_scan_found_group),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                observedCount.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        deviceItems.forEach { deviceItem ->
            key(deviceItem.detailKey) {
                LanDeviceCard(
                    presentation = deviceItem.card,
                    showMac = true,
                )
            }
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

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.VPN -> "VPN"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.UNKNOWN -> "未知网络"
}
