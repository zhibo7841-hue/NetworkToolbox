package com.networktoolbox.feature.report.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.feature.report.diagnostic.DiagnosticFinding
import com.networktoolbox.feature.report.diagnostic.DiagnosticReport
import com.networktoolbox.feature.report.diagnostic.FindingLevel
import com.networktoolbox.feature.report.domain.GenerateDiagnosticReportUseCase
import com.networktoolbox.feature.report.domain.ReportStep
import com.networktoolbox.feature.report.presentation.ReportProgress
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportUiState

@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onRunCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = uiState.status is ReportStatus.Running

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
                text = "网络诊断",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "执行一次本地网络检测并生成参考报告。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "固定目标：Ping ${GenerateDiagnosticReportUseCase.DEFAULT_PING_TARGET} · " +
                    "DNS ${GenerateDiagnosticReportUseCase.DEFAULT_DNS_DOMAIN} · " +
                    "TCP ${GenerateDiagnosticReportUseCase.DEFAULT_TCP_HOST}:" +
                    GenerateDiagnosticReportUseCase.DEFAULT_TCP_PORT,
                style = MaterialTheme.typography.bodySmall,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("操作", style = MaterialTheme.typography.titleMedium)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRunCheck,
                        enabled = !isRunning,
                    ) {
                        Text(if (isRunning) "检测中..." else "开始检测")
                    }
                }
            }

            when (val status = uiState.status) {
                ReportStatus.Idle -> Text("准备开始检测。")
                is ReportStatus.Running -> RunningContent(status.progress)
                is ReportStatus.Success -> ReportContent(status.report)
                is ReportStatus.Error -> ErrorContent(status.message)
            }
        }
    }
}

@Composable
private fun RunningContent(progress: ReportProgress) {
    Text("检测中...", style = MaterialTheme.typography.titleLarge)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportStep.entries.forEach { step ->
                StepRow(step = step, progress = progress)
            }
        }
    }
}

@Composable
private fun StepRow(
    step: ReportStep,
    progress: ReportProgress,
) {
    val marker = when {
        step in progress.completedSteps -> "✓"
        step == progress.activeStep -> "⏳"
        else -> "○"
    }
    Text("$marker ${step.displayName()}")
}

@Composable
private fun ReportContent(report: DiagnosticReport) {
    Text("结果", style = MaterialTheme.typography.titleLarge)
    ReportSectionCard(title = "摘要") {
        Text("状态：已完成", style = MaterialTheme.typography.bodyMedium)
        Text(report.summary)
    }
    ReportSectionCard(title = "发现") {
        if (report.findings.isEmpty()) {
            Text("未发现需要关注的现象。")
        } else {
            report.findings.forEach { finding -> FindingItem(finding) }
        }
    }
    ReportSectionCard(title = "建议") {
        if (report.suggestions.isEmpty()) {
            Text("暂无额外建议。")
        } else {
            report.suggestions.forEach { suggestion ->
                Text("• $suggestion")
            }
        }
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FindingItem(finding: DiagnosticFinding) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${finding.level.marker()} ${finding.level.displayName()} · ${finding.title}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorContent(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("状态：失败", style = MaterialTheme.typography.titleMedium)
            Text(message)
        }
    }
}

private fun ReportStep.displayName(): String = when (this) {
    ReportStep.NETWORK_INFORMATION -> "网络信息"
    ReportStep.PING -> "Ping"
    ReportStep.DNS -> "DNS"
    ReportStep.TCP -> "TCP"
}

private fun FindingLevel.displayName(): String = when (this) {
    FindingLevel.INFO -> "INFO"
    FindingLevel.WARNING -> "WARNING"
    FindingLevel.ERROR -> "ERROR"
}

private fun FindingLevel.marker(): String = when (this) {
    FindingLevel.INFO -> "✓"
    FindingLevel.WARNING -> "!"
    FindingLevel.ERROR -> "×"
}
