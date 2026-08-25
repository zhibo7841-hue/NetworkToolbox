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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.feature.ping.presentation.PingStatus
import com.networktoolbox.feature.ping.presentation.PingUiState

@Composable
fun PingScreen(
    uiState: PingUiState,
    onTargetChanged: (String) -> Unit,
    onPing: () -> Unit,
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
                text = "测试目标是否可达",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("输入", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.targetInput,
                        onValueChange = onTargetChanged,
                        label = { Text("目标地址") },
                        singleLine = true,
                        enabled = !isRunning,
                        isError = uiState.status.isInvalidInput(),
                    )
                    if (uiState.status.isInvalidInput()) {
                        Text(
                            "输入无效。请输入 IPv4 地址或域名。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPing,
                        enabled = !isRunning,
                    ) {
                        Text(if (isRunning) "检测中..." else "开始检测")
                    }
                }
            }

            when (val status = uiState.status) {
                PingStatus.Idle -> Unit
                is PingStatus.Running -> LoadingMessage()
                is PingStatus.Success -> PingResultCard(status.result)
                is PingStatus.Failed -> PingResultCard(status.result)
            }
        }
    }
}

@Composable
private fun PingResultCard(result: PingResult) {
    val success = result.success
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("结果", style = MaterialTheme.typography.titleMedium)
            ResultRow("目标", result.target.ifBlank { "未知" })
            ResultRow("状态", if (success) "已完成" else "失败")
            ResultRow("延迟", result.latencyMs?.let { "$it ms" } ?: "未知")
            ResultRow("检测方式", result.method.displayName())
            ResultRow("技术方法", result.method.name)
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ResultRow("Reason", errorMessage)
                    errorMessage.toExplanation()?.let { explanation ->
                        ResultRow("说明", explanation)
                    }
                }
        }
    }
}

@Composable
private fun LoadingMessage() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "检测中...",
            style = MaterialTheme.typography.bodyLarge,
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

private fun PingMethod.displayName(): String = when (this) {
    PingMethod.SYSTEM_REACHABILITY -> "系统可达性检测"
    PingMethod.UNAVAILABLE -> "不可用"
}

private fun PingStatus.isInvalidInput(): Boolean =
    this is PingStatus.Failed && result.errorMessage == "Invalid target."

private fun String.toExplanation(): String? = when (this) {
    "Invalid target." -> "请输入有效的 IPv4 地址或域名。"
    "Target could not be resolved." -> "目标无法解析，请检查地址或域名。"
    "Target is not reachable." -> "目标未响应本次系统可达性检测。"
    "System reachability is unavailable.", "Ping unavailable." ->
        "系统可达性检测暂时不可用。"
    else -> null
}
