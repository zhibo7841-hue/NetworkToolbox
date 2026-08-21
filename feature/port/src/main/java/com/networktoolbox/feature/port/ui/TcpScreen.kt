package com.networktoolbox.feature.port.ui

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isLoading) {
                Text("返回 Dashboard")
            }
            Text(
                text = "TCP Port Check",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "对指定 Host 和 Port 执行一次 TCP Connect",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.hostInput,
                onValueChange = onHostChanged,
                label = { Text("Host") },
                singleLine = true,
                enabled = !isLoading,
                isError = uiState.status.isInvalidHost(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.portInput,
                onValueChange = onPortChanged,
                label = { Text("Port") },
                singleLine = true,
                enabled = !isLoading,
                isError = uiState.status.isInvalidPort(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = onCheck,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "Checking..." else "Check")
            }

            when (val status = uiState.status) {
                TcpStatus.Idle -> Unit
                is TcpStatus.Loading -> Text("Checking...")
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
            Text("TCP Result", style = MaterialTheme.typography.titleLarge)
            ResultRow("Host", result.host.ifBlank { "未知" })
            ResultRow("Port", result.port.takeIf { it in 1..65_535 }?.toString() ?: "未知")
            ResultRow("Status", if (result.success) "Connected" else "Failed")
            ResultRow("Latency", result.latencyMs?.let { "$it ms" } ?: "未知")
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ResultRow("Reason", errorMessage.toDisplayError())
                    errorMessage.toExplanation()?.let { explanation ->
                        ResultRow("说明", explanation)
                    }
                }
            Spacer(modifier = Modifier.height(2.dp))
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

private fun TcpStatus.isInvalidHost(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid host."

private fun TcpStatus.isInvalidPort(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid port."

private fun String.toDisplayError(): String = when (this) {
    "Invalid host." -> "请输入 Host。"
    "Invalid port." -> "请输入有效端口。"
    "Timeout must be greater than zero." -> "超时时间无效。"
    else -> this
}

private fun String.toExplanation(): String? = when (this) {
    "Connection refused" -> "目标可达，但端口未开放。"
    "Timeout" -> "连接没有及时响应。"
    "Unknown error" -> "无法确定连接失败原因。"
    else -> null
}
