package com.networktoolbox.feature.port.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.port.presentation.TcpStatus
import com.networktoolbox.feature.port.presentation.TcpUiState

@Composable
fun TcpScreen(
    uiState: TcpUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState.status is TcpStatus.Loading

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isLoading) {
                Text("返回工具")
            }
            Text(
                text = "TCP Port Check",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "检查指定服务端口连接",
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
                        value = uiState.hostInput,
                        onValueChange = onHostChanged,
                        label = { Text("主机") },
                        singleLine = true,
                        enabled = !isLoading,
                        isError = uiState.status.isInvalidHost(),
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.portInput,
                        onValueChange = onPortChanged,
                        label = { Text("端口") },
                        singleLine = true,
                        enabled = !isLoading,
                        isError = uiState.status.isInvalidPort(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (uiState.status.isInvalidHost() || uiState.status.isInvalidPort()) {
                        Text(
                            "输入无效。请输入 Host 和 1–65535 范围内的端口。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCheck,
                        enabled = !isLoading,
                    ) {
                        Text(if (isLoading) "检测中..." else "开始检测")
                    }
                }
            }

            when (val status = uiState.status) {
                TcpStatus.Idle -> Unit
                is TcpStatus.Loading -> LoadingMessage()
                is TcpStatus.Success -> TcpResultCard(status.result)
                is TcpStatus.Error -> TcpResultCard(status.result)
            }
        }
    }
}

@Composable
private fun TcpResultCard(result: TcpProbeResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("结果", style = MaterialTheme.typography.titleMedium)
            ResultRow("主机", result.host.ifBlank { "未知" })
            ResultRow("端口", result.port.takeIf { it in 1..65_535 }?.toString() ?: "未知")
            ResultRow("状态", if (result.success) "已完成" else "失败")
            ResultRow("延迟", result.latencyMs?.let { "$it ms" } ?: "未知")
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

private fun TcpStatus.isInvalidHost(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid host."

private fun TcpStatus.isInvalidPort(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid port."

private fun String.toExplanation(): String? = when (this) {
    "Connection refused" -> "目标设备可访问，但该端口没有服务响应。"
    "Timeout" -> "连接没有及时响应。"
    "Unknown error" -> "无法确定连接失败原因。"
    else -> null
}
