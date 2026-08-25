package com.networktoolbox.feature.ping.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("检测设置", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.targetInput,
                        onValueChange = onTargetChanged,
                        label = { Text("目标地址或域名") },
                        singleLine = true,
                        enabled = !isRunning,
                        isError = uiState.status.isInputError(),
                    )
                    if (uiState.status.isInputError()) {
                        Text(
                            "请输入有效的 IPv4 地址或域名。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text("检测模式", style = MaterialTheme.typography.labelLarge)
                    ChoiceButton(
                        label = "快速检测",
                        selected = uiState.mode == PingDetectionMode.QUICK,
                        enabled = !isRunning,
                        onClick = { onModeChanged(PingDetectionMode.QUICK) },
                    )
                    ChoiceButton(
                        label = "连续检测",
                        selected = uiState.mode == PingDetectionMode.CONTINUOUS,
                        enabled = !isRunning,
                        onClick = { onModeChanged(PingDetectionMode.CONTINUOUS) },
                    )

                    Text("协议偏好", style = MaterialTheme.typography.labelLarge)
                    ProtocolChoice(
                        selected = uiState.protocol,
                        enabled = !isRunning,
                        onSelected = onProtocolChanged,
                    )

                    if (uiState.mode == PingDetectionMode.CONTINUOUS) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.countInput,
                            onValueChange = onCountChanged,
                            label = { Text("检测次数（1-100）") },
                            singleLine = true,
                            enabled = !isRunning,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.intervalInput,
                            onValueChange = onIntervalChanged,
                            label = { Text("间隔（毫秒）") },
                            singleLine = true,
                            enabled = !isRunning,
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (isRunning) onStop else onPing,
                    ) {
                        Text(if (isRunning) "停止检测" else "开始检测")
                    }
                }
            }

            when (val status = uiState.status) {
                PingStatus.Idle -> Unit
                is PingStatus.Running -> RunningCard(status)
                is PingStatus.Success -> PingResultCard(status.result)
                is PingStatus.Failed -> PingResultCard(status.result)
                is PingStatus.Cancelled -> CancelledCard(status.target)
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(label)
        }
    }
}

@Composable
private fun ProtocolChoice(
    selected: PingProtocol,
    enabled: Boolean,
    onSelected: (PingProtocol) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PingProtocol.entries.forEach { protocol ->
            ChoiceButton(
                label = protocol.displayName(),
                selected = selected == protocol,
                enabled = enabled,
                onClick = { onSelected(protocol) },
            )
        }
    }
}

@Composable
private fun RunningCard(status: PingStatus.Running) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("正在检测", style = MaterialTheme.typography.titleMedium)
            Text("目标：${status.target}")
            status.expectedCount?.let { Text("计划检测：$it 次") }
        }
    }
}

@Composable
private fun CancelledCard(target: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Ping 结果", style = MaterialTheme.typography.titleMedium)
            ResultRow("目标", result.target.ifBlank { "未知" })
            ResultRow("状态", if (completed) "已完成" else "失败")
            ResultRow("网络质量", result.qualityLevel.displayName())
            ResultRow("平均延迟", result.avgLatencyMs.latencyText())
            ResultRow("丢包率", result.packetLoss.percentText())
            ResultRow("摘要", result.summary)

            TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                Text(if (advancedExpanded) "收起详细信息" else "查看详细信息")
            }
            if (advancedExpanded) {
                ResultRow("检测协议", result.protocol.displayName())
                ResultRow("地址", result.address ?: "未解析")
                ResultRow("发送", result.sentPackets.toString())
                ResultRow("接收", result.receivedPackets.toString())
                ResultRow("丢包", "${result.lostPackets}（${result.packetLoss.percentText()}）")
                ResultRow("最低延迟", result.minLatencyMs?.let { "$it ms" } ?: "未检测到")
                ResultRow("最高延迟", result.maxLatencyMs?.let { "$it ms" } ?: "未检测到")
                ResultRow("抖动", result.jitterMs.latencyText())
                ResultRow("检测方式", result.method.displayName())
                result.errorMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { errorMessage ->
                        ResultRow("原因", errorMessage)
                        errorMessage.toExplanation()?.let { explanation ->
                            ResultRow("说明", explanation)
                        }
                    }
            }
        }
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

private fun PingMethod.displayName(): String = when (this) {
    PingMethod.SYSTEM_REACHABILITY -> "系统可达性检测"
    PingMethod.UNAVAILABLE -> "不可用"
}

private fun PingStatus.isInputError(): Boolean = this is PingStatus.Failed &&
    result.errorMessage in setOf("Invalid target.", "Invalid count.", "Invalid interval.")

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
