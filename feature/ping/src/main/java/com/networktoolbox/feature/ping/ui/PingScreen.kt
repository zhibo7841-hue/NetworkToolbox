package com.networktoolbox.feature.ping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.designsystem.DestructiveActionButton
import com.networktoolbox.core.designsystem.NetworkToolAccent
import com.networktoolbox.core.designsystem.NetworkToolboxSpacing
import com.networktoolbox.core.designsystem.NetworkToolboxTextStyles
import com.networktoolbox.core.designsystem.OutlinedNetworkCard
import com.networktoolbox.core.designsystem.PrimaryActionButton
import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.designsystem.ToolInputSection
import com.networktoolbox.core.designsystem.ToolMetric
import com.networktoolbox.core.designsystem.ToolMetricGrid
import com.networktoolbox.core.designsystem.ToolResultRow
import com.networktoolbox.core.designsystem.ToolRunningSection
import com.networktoolbox.core.designsystem.ToolScreenHeader
import com.networktoolbox.core.designsystem.ToolScreenLayout
import com.networktoolbox.core.designsystem.ToolStatusSummary
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.feature.ping.presentation.PingDetectionMode
import com.networktoolbox.feature.ping.presentation.PingStatus
import com.networktoolbox.feature.ping.presentation.PingUiState
import java.util.Locale

@Composable
fun PingScreen(
    uiState: PingUiState,
    onTargetChanged: (String) -> Unit,
    onModeChanged: (PingDetectionMode) -> Unit,
    onProtocolChanged: (PingProtocol) -> Unit,
    onCountChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onPing: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = uiState.status is PingStatus.Running
    val inputErrorMessage = uiState.status.inputErrorMessage()
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            advancedSettingsExpanded = false
        }
    }

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = "Ping",
            description = null,
            icon = Icons.Outlined.WifiTethering,
            accent = NetworkToolAccent.PRIMARY,
            onBack = onBack,
            backEnabled = !isRunning,
        )

        if (isRunning) {
            RunningCard(
                status = uiState.status as PingStatus.Running,
                onStop = onStop,
            )
        } else {
            ToolInputSection(title = "目标") {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.targetInput,
                    onValueChange = onTargetChanged,
                    label = { Text("目标地址或域名") },
                    singleLine = true,
                    isError = uiState.status.isTargetInputError(),
                )
                inputErrorMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                PrimaryActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPing,
                ) {
                    Text("开始检测")
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                ) {
                    Text(if (advancedSettingsExpanded) "收起高级设置" else "高级设置 >")
                }

                if (advancedSettingsExpanded) {
                    AdvancedSettings(
                        uiState = uiState,
                        onModeChanged = onModeChanged,
                        onProtocolChanged = onProtocolChanged,
                        onCountChanged = onCountChanged,
                        onIntervalChanged = onIntervalChanged,
                    )
                }
            }

            when (val status = uiState.status) {
                PingStatus.Idle -> Unit
                is PingStatus.Success -> PingResultCard(status.result)
                is PingStatus.Failed -> PingResultCard(status.result)
                is PingStatus.Cancelled -> CancelledCard(status.target)
                is PingStatus.Running -> Unit
            }
        }
    }
}

@Composable
private fun AdvancedSettings(
    uiState: PingUiState,
    onModeChanged: (PingDetectionMode) -> Unit,
    onProtocolChanged: (PingProtocol) -> Unit,
    onCountChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
        Text("检测模式", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = uiState.mode == PingDetectionMode.QUICK,
                onClick = { onModeChanged(PingDetectionMode.QUICK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text("快速检测") },
            )
            SegmentedButton(
                selected = uiState.mode == PingDetectionMode.CONTINUOUS,
                onClick = { onModeChanged(PingDetectionMode.CONTINUOUS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text("连续检测") },
            )
        }

        Text("协议偏好", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PingProtocol.entries.forEachIndexed { index, protocol ->
                SegmentedButton(
                    selected = uiState.protocol == protocol,
                    onClick = { onProtocolChanged(protocol) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PingProtocol.entries.size,
                    ),
                    modifier = Modifier.weight(1f),
                    label = { Text(protocol.displayName()) },
                )
            }
        }

        if (uiState.mode == PingDetectionMode.CONTINUOUS) {
            Row(horizontalArrangement = Arrangement.spacedBy(NetworkToolboxSpacing.SM)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = uiState.countInput,
                    onValueChange = onCountChanged,
                    label = { Text("次数（1-100）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.status.isCountInputError(),
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = uiState.intervalInput,
                    onValueChange = onIntervalChanged,
                    label = { Text("间隔（毫秒）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.status.isIntervalInputError(),
                )
            }
        }
    }
}

@Composable
private fun RunningCard(
    status: PingStatus.Running,
    onStop: () -> Unit,
) {
    ToolRunningSection {
        ToolStatusSummary(
            title = if (status.expectedCount == null) "正在连续检测" else "正在检测",
            status = StatusVisualState.RUNNING,
            label = "检测中",
        )
        Text(status.target, style = NetworkToolboxTextStyles.TechnicalData)
        status.expectedCount?.let { expectedCount ->
            val progress = if (expectedCount == 0) {
                0f
            } else {
                (status.completedCount.toFloat() / expectedCount).coerceIn(0f, 1f)
            }
            Text("已完成：${status.completedCount} / $expectedCount")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric("当前", status.latestLatencyMs?.let { "$it ms" } ?: "未收到"),
                ToolMetric("平均", status.avgLatencyMs.latencyText()),
                ToolMetric("最低", status.minLatencyMs?.let { "$it ms" } ?: "未收到"),
                ToolMetric("最高", status.maxLatencyMs?.let { "$it ms" } ?: "未收到"),
                ToolMetric("丢包", status.packetLoss.percentText()),
            ),
        )
        Text(
            "检测过程中可随时停止。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DestructiveActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStop,
        ) {
            Text("停止检测")
        }
    }
}

@Composable
private fun CancelledCard(target: String) {
    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "检测已停止",
            status = StatusVisualState.CANCELLED,
            label = "已停止",
        )
        ToolResultRow(
            label = "目标",
            value = target.ifBlank { "未知" },
            valueStyle = NetworkToolboxTextStyles.TechnicalData,
        )
        Text(
            "本次未生成完整结果，也不会写入历史记录。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PingResultCard(result: PingSessionResult) {
    var advancedExpanded by rememberSaveable(result.target, result.endTime) {
        mutableStateOf(false)
    }
    val completed = result.receivedPackets > 0

    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "${result.target.ifBlank { "未知" }} · ${if (completed) "已完成" else "无法访问"}",
            status = result.qualityLevel.statusVisualState(),
            label = result.qualityLevel.statusLabel(),
        )
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric("平均延迟", result.avgLatencyMs.latencyText()),
                ToolMetric("丢包率", result.packetLoss.percentText()),
            ),
        )
        Text(result.localizedSummary())

        if (!completed) {
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ToolResultRow("原因", errorMessage.displayMessage())
                    errorMessage.toExplanation()?.let { explanation ->
                        Text(
                            explanation,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
        }

        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(if (advancedExpanded) "收起详细信息" else "查看详细信息")
        }
        if (advancedExpanded) {
            HorizontalDivider()
            Text("基本信息", style = MaterialTheme.typography.labelLarge)
            ToolResultRow("检测协议", result.protocol.displayName())
            ToolResultRow(
                "地址",
                result.address ?: "未解析",
                valueStyle = NetworkToolboxTextStyles.TechnicalData,
            )
            ToolResultRow("检测方式", result.method.displayName())
            Text("数据包", style = MaterialTheme.typography.labelLarge)
            ToolResultRow("发送", result.sentPackets.toString())
            ToolResultRow("接收", result.receivedPackets.toString())
            ToolResultRow("丢包", "${result.lostPackets}（${result.packetLoss.percentText()}）")
            Text("延迟", style = MaterialTheme.typography.labelLarge)
            ToolResultRow("最低延迟", result.minLatencyMs?.let { "$it ms" } ?: "未检测到")
            ToolResultRow("平均延迟", result.avgLatencyMs.latencyText())
            ToolResultRow("最高延迟", result.maxLatencyMs?.let { "$it ms" } ?: "未检测到")
            ToolResultRow("抖动", result.jitterMs.latencyText())
        }
    }
}

private fun PingQualityLevel.statusVisualState(): StatusVisualState = when (this) {
    PingQualityLevel.EXCELLENT,
    PingQualityLevel.GOOD,
    -> StatusVisualState.NORMAL

    PingQualityLevel.FAIR,
    PingQualityLevel.POOR,
    -> StatusVisualState.NOTICE

    PingQualityLevel.UNKNOWN -> StatusVisualState.UNKNOWN
}

private fun PingQualityLevel.statusLabel(): String = when (this) {
    PingQualityLevel.EXCELLENT -> "网络质量优秀"
    PingQualityLevel.GOOD -> "网络质量良好"
    PingQualityLevel.FAIR -> "网络质量一般"
    PingQualityLevel.POOR -> "网络质量较差"
    PingQualityLevel.UNKNOWN -> "网络质量未确定"
}

private fun PingProtocol.displayName(): String = when (this) {
    PingProtocol.AUTO -> "自动选择"
    PingProtocol.IPV4 -> "IPv4"
    PingProtocol.IPV6 -> "IPv6"
}

private fun PingSessionResult.localizedSummary(): String = when (qualityLevel) {
    PingQualityLevel.EXCELLENT ->
        "网络连接稳定，未检测到明显丢包。"
    PingQualityLevel.GOOD ->
        "网络连接较好，当前检测到的延迟和丢包处于较低水平。"
    PingQualityLevel.FAIR ->
        "网络可达，但存在一定延迟波动。"
    PingQualityLevel.POOR ->
        "网络质量较差，存在明显延迟或丢包。"
    PingQualityLevel.UNKNOWN ->
        "本次未能获得有效响应，暂时无法评价网络质量。"
}

private fun PingMethod.displayName(): String = when (this) {
    PingMethod.SYSTEM_REACHABILITY -> "系统可达性检测"
    PingMethod.UNAVAILABLE -> "不可用"
}

private fun PingStatus.inputErrorMessage(): String? =
    (this as? PingStatus.Failed)?.result?.errorMessage?.let { errorMessage ->
        when (errorMessage) {
            "Invalid target." -> "请输入有效的 IPv4 地址或域名。"
            "Invalid count." -> "检测次数需要在 1 到 100 之间。"
            "Invalid interval." -> "检测间隔需要在 100 到 60000 毫秒之间。"
            else -> null
        }
    }

private fun PingStatus.isTargetInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid target."

private fun PingStatus.isCountInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid count."

private fun PingStatus.isIntervalInputError(): Boolean =
    (this as? PingStatus.Failed)?.result?.errorMessage == "Invalid interval."

private fun String.toExplanation(): String? = when (this) {
    "Invalid target." -> "请输入有效的 IPv4 地址或域名。"
    "Target could not be resolved." -> "目标无法解析，请检查地址或域名。"
    "No IPv4 address available." -> "目标没有可用的 IPv4 地址。"
    "No IPv6 address available." -> "目标没有可用的 IPv6 地址。"
    "Target is not reachable." -> "目标未响应本次系统可达性检测。"
    "Timeout" -> "检测在设定时间内未收到目标响应。"
    "System reachability is unavailable.", "Ping unavailable." ->
        "系统可达性检测暂时不可用。"
    else -> null
}

private fun String.displayMessage(): String = when (this) {
    "Invalid target." -> "目标地址无效"
    "Invalid count." -> "检测次数无效"
    "Invalid interval." -> "检测间隔无效"
    "Target could not be resolved." -> "目标无法解析"
    "No IPv4 address available." -> "目标没有可用的 IPv4 地址"
    "No IPv6 address available." -> "目标没有可用的 IPv6 地址"
    "Target is not reachable." -> "目标无响应"
    "Timeout" -> "检测超时"
    "System reachability is unavailable.", "Ping unavailable." -> "系统可达性检测不可用"
    else -> "无法完成 Ping 检测"
}

private fun Double?.latencyText(): String = this?.let { value ->
    if (value == value.toLong().toDouble()) {
        "${value.toLong()} ms"
    } else {
        String.format(Locale.US, "%.1f ms", value)
    }
} ?: "未检测到"

private fun Long?.latencyText(): String = this?.let { "$it ms" } ?: "未检测到"

private fun Double.percentText(): String = String.format(Locale.US, "%.1f%%", this)
