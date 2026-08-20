package com.networktoolbox.feature.ping.ui

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack, enabled = !isRunning) {
                Text("返回 Dashboard")
            }
            Text(
                text = "Ping",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "检测 IPv4 地址或域名的系统网络可达性",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.targetInput,
                onValueChange = onTargetChanged,
                label = { Text("Target") },
                singleLine = true,
                enabled = !isRunning,
                isError = uiState.status.isInvalidInput(),
            )
            Button(
                onClick = onPing,
                enabled = !isRunning,
            ) {
                Text(if (isRunning) "正在检测..." else "Ping")
            }

            when (val status = uiState.status) {
                PingStatus.Idle -> Unit
                is PingStatus.Running -> Text("正在检测...")
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
            Text("检测结果", style = MaterialTheme.typography.titleLarge)
            ResultRow("目标", result.target.ifBlank { "未知" })
            ResultRow("状态", if (success) "可达" else "不可达")
            ResultRow("延迟", result.latencyMs?.let { "$it ms" } ?: "未知")
            ResultRow("检测方式", result.method.displayName())
            ResultRow("Method", result.method.name)
            result.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { errorMessage ->
                    ResultRow("原因", errorMessage.toDisplayError())
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

private fun PingMethod.displayName(): String = when (this) {
    PingMethod.SYSTEM_REACHABILITY -> "系统可达性检测"
    PingMethod.UNAVAILABLE -> "不可用"
}

private fun PingStatus.isInvalidInput(): Boolean =
    this is PingStatus.Failed && result.errorMessage == "Invalid target."

private fun String.toDisplayError(): String =
    if (this == "Invalid target.") "输入无效。" else this
