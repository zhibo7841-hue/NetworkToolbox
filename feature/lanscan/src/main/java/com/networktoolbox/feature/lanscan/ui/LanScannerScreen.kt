package com.networktoolbox.feature.lanscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.NetworkToolboxTopLevelHeader
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.SecondaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolResultSection
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
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
) {
    ToolScreenLayout(modifier = modifier) {
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
            )

            is LanScannerUiState.Scanning -> ScanningContent(
                state = state,
                onStopScan = onStopScan,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Completed -> SessionContent(
                title = "扫描完成",
                session = state.session,
                actionLabel = "重新扫描",
                onAction = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.Cancelled -> SessionContent(
                title = "扫描已停止",
                session = state.session,
                actionLabel = "重新扫描",
                onAction = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
            )

            is LanScannerUiState.NetworkChanged -> NetworkChangedContent(
                session = state.session,
                onRetry = onRetry,
                onModifyRange = onModifyRange,
                savedProfiles = savedProfiles,
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

    PrivacyHint()
    PrimaryActionButton(
        onClick = onStartScan,
        enabled = rangeMode == LanScanRangeMode.CURRENT_NETWORK ||
            customRangeResult is LanCustomRangeResult.Valid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("开始扫描")
    }
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
    ToolRunningSection {
        ToolStatusSummary(
            title = "正在扫描",
            status = StatusVisualState.RUNNING,
            label = "扫描中",
        )
        Text(
            "范围：${state.range.displayLabel}",
            style = NetworkToolboxTextStyles.TechnicalData,
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
    }
    SecondaryActionButton(
        onClick = onStopScan,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("停止扫描")
    }
    DeviceList(update.discoveredDevices, state.networkContext, savedProfiles)
}

@Composable
private fun SessionContent(
    title: String,
    session: LanScanSession,
    actionLabel: String,
    onAction: () -> Unit,
    onModifyRange: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    ToolResultSection {
        ToolStatusSummary(
            title = title,
            status = if (session.status == LanScanStatus.COMPLETED) {
                StatusVisualState.NORMAL
            } else {
                StatusVisualState.CANCELLED
            },
            label = if (session.status == LanScanStatus.COMPLETED) "已完成" else "已停止",
        )
        session.range?.let { range ->
            Text(range.displayLabel, style = NetworkToolboxTextStyles.TechnicalData)
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
    }
    SecondaryActionButton(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(actionLabel)
    }
    TextButton(onClick = onModifyRange) {
        Text("修改扫描范围 >")
    }
    DeviceList(session.discoveredDevices, session.initialNetworkContext, savedProfiles)
}

@Composable
private fun NetworkChangedContent(
    session: LanScanSession,
    onRetry: () -> Unit,
    onModifyRange: () -> Unit,
    savedProfiles: List<FavoriteDevice>,
) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "网络已发生变化",
            status = StatusVisualState.NOTICE,
            label = "网络变化",
        )
        Text("扫描已停止，以避免混合不同局域网的结果。")
        Text(
            "请在网络稳定后重新扫描。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("已扫描 ${session.scannedHosts} / ${session.totalHosts} 个地址")
    }
    SecondaryActionButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重新扫描")
    }
    TextButton(onClick = onModifyRange) {
        Text("修改扫描范围 >")
    }
    DeviceList(session.discoveredDevices, session.initialNetworkContext, savedProfiles)
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
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "扫描失败",
            status = StatusVisualState.ERROR,
            label = "失败",
        )
        Text(message)
    }
    SecondaryActionButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重试")
    }
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
private fun PrivacyHint() {
    Text(
        "扫描只在当前本地网络中进行，结果保存在设备本地，不会上传。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeviceList(
    devices: List<LanDevice>,
    context: NetworkContext,
    savedProfiles: List<FavoriteDevice>,
) {
    if (devices.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
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
        devices.forEach { device ->
            val profile = LanFavoriteIdentity.candidate(device, context)?.let { candidate ->
                savedProfiles.firstOrNull { saved ->
                    FavoriteIdentityMatcher.matches(saved, candidate)
                }
            }
            LanDeviceCard(device = device, showMac = true, savedProfile = profile)
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
