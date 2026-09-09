package com.networktoolbox.feature.port.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import com.networktoolbox.core.designsystem.NetworkToolAccent
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

    ToolScreenLayout(modifier = modifier) {
        ToolScreenHeader(
            title = "TCP 端口检测",
            description = null,
            icon = Icons.Outlined.Lan,
            accent = NetworkToolAccent.AMBER,
            onBack = onBack,
            backEnabled = !isLoading,
        )

        ToolInputSection(title = "连接目标") {
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
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCheck,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "检测中..." else "开始检测")
            }
        }

        when (val status = uiState.status) {
            TcpStatus.Idle -> Unit
            is TcpStatus.Loading -> LoadingMessage(status.host, status.port)
            is TcpStatus.Success -> TcpResultCard(status.result)
            is TcpStatus.Error -> TcpResultCard(status.result)
        }
    }
}

@Composable
private fun LoadingMessage(host: String, port: String) {
    ToolRunningSection {
        ToolStatusSummary(
            title = "正在检测",
            status = StatusVisualState.RUNNING,
            label = "检测中",
        )
        ToolResultRow("目标", "$host:$port", valueStyle = NetworkToolboxTextStyles.TechnicalData)
    }
}

@Composable
private fun TcpResultCard(result: TcpProbeResult) {
    val presentation = result.presentation()

    OutlinedNetworkCard {
        ToolStatusSummary(
            title = "TCP 结果",
            status = presentation.status,
            label = presentation.headline,
        )
        ToolMetricGrid(
            metrics = listOf(
                ToolMetric("主机", result.host.ifBlank { "未知" }),
                ToolMetric(
                    "端口",
                    result.port.takeIf { it in 1..65_535 }?.toString() ?: "未知",
                ),
                ToolMetric("延迟", result.latencyMs?.let { "$it ms" } ?: "未知"),
            ),
        )
        presentation.explanation?.let { explanation ->
            Text(
                explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TcpResultPresentation(
    val status: StatusVisualState,
    val headline: String,
    val explanation: String?,
)

private fun TcpProbeResult.presentation(): TcpResultPresentation {
    outcome?.let { typedOutcome ->
        return when (typedOutcome) {
            DiagnosticTcpOutcome.CONNECT_SUCCESS -> TcpResultPresentation(
                status = StatusVisualState.NORMAL,
                headline = "已连接",
                explanation = "目标端口接受了 TCP 连接。",
            )

            DiagnosticTcpOutcome.CONNECTION_REFUSED -> TcpResultPresentation(
                status = StatusVisualState.NOTICE,
                headline = "连接被拒绝",
                explanation = "目标设备可访问，但该端口没有服务响应。",
            )

            DiagnosticTcpOutcome.TIMEOUT -> TcpResultPresentation(
                status = StatusVisualState.NOTICE,
                headline = "连接超时",
                explanation = "目标没有在规定时间内响应 TCP 连接。",
            )

            DiagnosticTcpOutcome.NETWORK_UNREACHABLE,
            DiagnosticTcpOutcome.NO_ROUTE,
            -> TcpResultPresentation(
                status = StatusVisualState.ERROR,
                headline = "无法到达目标",
                explanation = "当前目标未能完成连接，可能与网络路径或路由有关。",
            )

            DiagnosticTcpOutcome.UNKNOWN,
            DiagnosticTcpOutcome.INTERNAL_ERROR,
            -> TcpResultPresentation(
                status = StatusVisualState.UNKNOWN,
                headline = "结果未确定",
                explanation = "无法确定本次 TCP 连接的具体结果。",
            )
        }
    }

    if (success) {
        return TcpResultPresentation(
            status = StatusVisualState.NORMAL,
            headline = "已连接",
            explanation = "目标端口接受了 TCP 连接。",
        )
    }

    return when (errorMessage) {
        "Connection refused" -> TcpResultPresentation(
            status = StatusVisualState.NOTICE,
            headline = "连接被拒绝",
            explanation = "目标设备可访问，但该端口没有服务响应。",
        )

        "Timeout" -> TcpResultPresentation(
            status = StatusVisualState.NOTICE,
            headline = "连接超时",
            explanation = "目标没有在规定时间内响应 TCP 连接。",
        )

        "Invalid host.", "Invalid port." -> TcpResultPresentation(
            status = StatusVisualState.UNKNOWN,
            headline = "输入无效",
            explanation = "请检查主机地址和端口范围。",
        )

        else -> TcpResultPresentation(
            status = StatusVisualState.UNKNOWN,
            headline = "结果未确定",
            explanation = "无法确定本次 TCP 连接的具体结果。",
        )
    }
}

private fun TcpStatus.isInvalidHost(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid host."

private fun TcpStatus.isInvalidPort(): Boolean =
    this is TcpStatus.Error && result.errorMessage == "Invalid port."
