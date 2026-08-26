package com.networktoolbox.feature.ping.ui

import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isRunning) {
                Text("返回工具")
            }
            Text(
                text = "Ping",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "分析目标的可达性与网络质量",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isRunning) {
                RunningCard(
                    status = uiState.status as PingStatus.Running,
                    onStop = onStop,
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("目标", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.targetInput,
                            onValueChange = onTargetChanged,
                            label = { Text("目标地址或域名") },
                            singleLine = true,
                            isError = uiState.status.isTargetInputError(),
                        )
                        if (inputErrorMessage != null) {
                            Text(
                                inputErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onPing,
                        ) {
                            Text("开始检测")
                        }

                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                        ) {
                            Text(
                                if (advancedSettingsExpanded) {
                                    "收起高级设置"
                                } else {
                                    "高级设置 >"
                                },
                            )
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
}

@Composable
private fun AdvancedSettings(
    uiState: PingUiState,
    onModeChanged: (PingDetectionMode) -> Unit,
    onProtocolChanged: (PingProtocol) -> Unit,
    onCountChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (status.expectedCount == null) "正在连续检测" else "正在检测",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(status.target)
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBlock(
                    label = "当前",
                    value = status.latestLatencyMs?.let { "$it ms" } ?: "未收到",
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "平均",
                    value = status.avgLatencyMs.latencyText(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBlock(
                    label = "最低",
                    value = status.minLatencyMs?.let { "$it ms" } ?: "未收到",
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "最高",
                    value = status.maxLatencyMs?.let { "$it ms" } ?: "未收到",
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "丢包",
                    value = status.packetLoss.percentText(),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "检测过程中可随时停止。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStop,
            ) {
                Text("停止检测")
            }
        }
    }
}

@Composable
private fun CancelledCard(target: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("检测已停止", style = MaterialTheme.typography.titleMedium)
            Text("目标：${target.ifBlank { "未知" }}")
            Text(
                "本次未生成完整结果，也不会写入历史记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PingResultCard(result: PingSessionResult) {
    var advancedExpanded by rememberSaveable(result.target, result.endTime) {
        mutableStateOf(false)
    }
    val completed = result.receivedPackets > 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${result.target.ifBlank { "未知" }} · ${if (completed) "已完成" else "无法访问"}",
                style = MaterialTheme.typography.titleMedium,
            )
            QualityBadge(result.qualityLevel)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBlock(
                    label = "平均延迟",
                    value = result.avgLatencyMs.latencyText(),
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "丢包率",
                    value = result.packetLoss.percentText(),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(result.localizedSummary())
            if (!completed) {
                result.errorMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { errorMessage ->
                        ResultRow("原因", errorMessage)
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
                Text("基本信息", style = MaterialTheme.typography.labelLarge)
                ResultRow("检测协议", result.protocol.displayName())
                ResultRow("地址", result.address ?: "未解析")
                ResultRow("检测方式", result.method.displayName())
                Text("数据包", style = MaterialTheme.typography.labelLarge)
                ResultRow("发送", result.sentPackets.toString())
                ResultRow("接收", result.receivedPackets.toString())
                ResultRow("丢包", "${result.lostPackets}（${result.packetLoss.percentText()}）")
                Text("延迟", style = MaterialTheme.typography.labelLarge)
                ResultRow("最低延迟", result.minLatencyMs?.let { "$it ms" } ?: "未检测到")
                ResultRow("平均延迟", result.avgLatencyMs.latencyText())
                ResultRow("最高延迟", result.maxLatencyMs?.let { "$it ms" } ?: "未检测到")
                ResultRow("抖动", result.jitterMs.latencyText())
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun QualityBadge(level: PingQualityLevel) {
    val containerColor = when (level) {
        PingQualityLevel.EXCELLENT,
        PingQualityLevel.GOOD,
        -> MaterialTheme.colorScheme.primaryContainer
        PingQualityLevel.FAIR -> MaterialTheme.colorScheme.secondaryContainer
        PingQualityLevel.POOR -> MaterialTheme.colorScheme.errorContainer
        PingQualityLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (level) {
        PingQualityLevel.EXCELLENT,
        PingQualityLevel.GOOD,
        -> MaterialTheme.colorScheme.onPrimaryContainer
        PingQualityLevel.FAIR -> MaterialTheme.colorScheme.onSecondaryContainer
        PingQualityLevel.POOR -> MaterialTheme.colorScheme.onErrorContainer
        PingQualityLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = "${level.emoji()} 网络质量${level.displayName()}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(0.4f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            modifier = Modifier.weight(0.6f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun PingProtocol.displayName(): String = when (this) {
    PingProtocol.AUTO -> "自动选择"
    PingProtocol.IPV4 -> "IPv4"
    PingProtocol.IPV6 -> "IPv6"
}

private fun PingQualityLevel.displayName(): String = when (this) {
    PingQualityLevel.EXCELLENT -> "优秀"
    PingQualityLevel.GOOD -> "良好"
    PingQualityLevel.FAIR -> "一般"
    PingQualityLevel.POOR -> "较差"
    PingQualityLevel.UNKNOWN -> "暂无评价"
}

private fun PingQualityLevel.emoji(): String = when (this) {
    PingQualityLevel.EXCELLENT, PingQualityLevel.GOOD -> "🟢"
    PingQualityLevel.FAIR -> "🟡"
    PingQualityLevel.POOR -> "🔴"
    PingQualityLevel.UNKNOWN -> "⚪"
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
    "System reachability is unavailable.", "Ping unavailable." ->
        "系统可达性检测暂时不可用。"
    else -> null
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
