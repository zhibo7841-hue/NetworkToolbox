package com.networktoolbox.feature.report.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.presentation.ReportProgress
import com.networktoolbox.feature.report.presentation.ReportStageStatus
import com.networktoolbox.feature.report.presentation.ReportStatus
import com.networktoolbox.feature.report.presentation.ReportUiState
import com.networktoolbox.feature.report.presentation.diagnosticStages

@Composable
fun ReportScreen(
    uiState: ReportUiState,
    restoredReport: DiagnosticReportV2? = null,
    onRunCheck: () -> Unit,
    onStopCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = restoredReport == null && uiState.status is ReportStatus.Running

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
            Text("网络诊断", style = MaterialTheme.typography.headlineSmall)
            Text(
                "自动检查当前网络环境并定位常见连接问题。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isRunning && restoredReport == null) {
                StartDiagnosticCard(
                    status = uiState.status,
                    onRunCheck = onRunCheck,
                )
            }

            when {
                restoredReport != null -> ReportContent(
                    report = restoredReport,
                    restored = true,
                )

                uiState.status is ReportStatus.Running -> RunningContent(
                    progress = uiState.status.progress,
                    onStopCheck = onStopCheck,
                )

                uiState.status is ReportStatus.Success -> ReportContent(
                    report = uiState.status.report,
                    restored = false,
                )

                uiState.status is ReportStatus.Cancelled -> CancelledContent(
                    onRunCheck = onRunCheck,
                )

                uiState.status is ReportStatus.Error -> ErrorContent(
                    message = uiState.status.message,
                    onRetry = onRunCheck,
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun StartDiagnosticCard(
    status: ReportStatus,
    onRunCheck: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("开始一次完整诊断", style = MaterialTheme.typography.titleMedium)
            Text(
                "检测将在本机完成，不上传诊断数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRunCheck,
            ) {
                Text(if (status is ReportStatus.Success) "重新诊断" else "开始诊断")
            }
        }
    }
}

@Composable
private fun RunningContent(
    progress: ReportProgress,
    onStopCheck: () -> Unit,
) {
    Text("正在诊断…", style = MaterialTheme.typography.titleLarge)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            diagnosticStages.forEach { stage ->
                StageProgressRow(
                    stage = stage,
                    status = progress.stageStates[stage] ?: ReportStageStatus.PENDING,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStopCheck,
            ) {
                Text("停止诊断")
            }
        }
    }
}

@Composable
private fun StageProgressRow(
    stage: DiagnosticStage,
    status: ReportStageStatus,
) {
    val (marker, label, color) = when (status) {
        ReportStageStatus.COMPLETED -> Triple("✓", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.RUNNING -> Triple("→", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.FAILED -> Triple("×", stage.displayName(), MaterialTheme.colorScheme.error)
        ReportStageStatus.SKIPPED -> Triple("－", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.PENDING -> Triple("○", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("$marker $label", color = color)
}

@Composable
private fun ReportContent(
    report: DiagnosticReportV2,
    restored: Boolean,
) {
    var detailsExpanded by rememberSaveable(report.timestamp, restored) {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportOverview(report)

        ReportSectionCard(title = "诊断结论") {
            Text(report.summary, style = MaterialTheme.typography.bodyLarge)
        }

        ReportSectionCard(title = "排查结果") {
            val checks = report.checks.filter { it.stage in DISPLAY_STAGES }
            if (checks.isEmpty()) {
                Text("暂无阶段结果。")
            } else {
                checks.forEach { check -> DiagnosticCheckRow(check) }
            }
        }

        ReportSectionCard(title = "发现") {
            if (report.findings.isEmpty()) {
                Text("未发现需要关注的现象。")
            } else {
                report.findings.forEach { finding -> FindingItem(finding) }
            }
        }

        ReportSectionCard(title = "建议") {
            if (report.recommendations.isEmpty()) {
                Text("暂无额外建议。")
            } else {
                report.recommendations
                    .sortedBy { it.priority }
                    .take(3)
                    .forEach { recommendation ->
                        Text(
                            "${recommendation.priority}. ${recommendation.action}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        recommendation.reason?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
            }
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { detailsExpanded = !detailsExpanded },
        ) {
            Text(if (detailsExpanded) "收起详细信息" else "查看详细信息")
        }
        if (detailsExpanded) {
            DiagnosticDetails(report)
        }
    }
}

@Composable
private fun ReportOverview(report: DiagnosticReportV2) {
    val (statusText, statusColor) = report.overallStatus.displayInfo()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("诊断完成", style = MaterialTheme.typography.titleLarge)
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCheckRow(check: DiagnosticCheck) {
    val (marker, statusText, color) = check.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$marker ${check.displayName()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(statusText, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            check.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FindingItem(finding: DiagnosticFindingV2) {
    val (marker, severityText, color) = finding.severity.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$marker $severityText · ${finding.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiagnosticDetails(report: DiagnosticReportV2) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReportSectionCard(title = "网络环境") {
            val context = report.networkSnapshot
            if (context == null) {
                Text("未获得网络环境信息。")
            } else {
                ResultRow("网络类型", context.connectionType.displayName())
                ResultRow("IPv4", context.ipv4Address ?: "未检测到")
                ResultRow("IPv6", context.ipv6Address ?: "未检测到")
                ResultRow("VPN", context.vpnActive.toEnabledText())
                ResultRow("VALIDATED", context.validated.toYesNoText())
                Text("网络配置 DNS", style = MaterialTheme.typography.labelLarge)
                if (context.dnsServers.isEmpty()) {
                    Text("未检测到", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    context.dnsServers.forEach { dns -> Text(dns) }
                }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.GATEWAY }?.let { check ->
            ReportSectionCard(title = "网关") {
                ResultRow("目标", check.target ?: "未提供")
                ResultRow("结果", check.status.displayName())
                check.method?.let { ResultRow("检测方式", it.methodDisplayName()) }
                check.rawData["avgLatencyMs"]?.let { ResultRow("平均延迟", "$it ms") }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.PUBLIC_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = "公网") {
                check.rawData["targetOutcomes"]
                    ?.split(';')
                    ?.filter(String::isNotBlank)
                    ?.forEach { outcome ->
                        Text(formatTargetOutcome(outcome))
                    }
                ResultRow("综合判断", check.status.displayName())
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DNS }?.let { check ->
            ReportSectionCard(title = "DNS") {
                ResultRow("查询域名", check.target ?: "未提供")
                ResultRow("状态", check.status.displayName())
                check.rawData["requestedTypes"]?.let { ResultRow("查询类型", it) }
                check.rawData["recordCounts"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { count ->
                        val type = count.substringBefore('=')
                        val value = count.substringAfter('=', "0")
                        ResultRow("$type 记录", "$value 条")
                    }
                check.rawData["durationMs"]
                    ?.takeUnless { it == "unknown" }
                    ?.let { ResultRow("查询耗时", "$it ms") }
                check.rawData["recordCount"]?.let { ResultRow("记录数量", it) }
                check.method?.let { ResultRow("查询方式", it.methodDisplayName()) }
                check.rawData["fakeIpObserved"]?.toBooleanStrictOrNull()
                    ?.takeIf { it }
                    ?.let { Text("提示：检测到特殊用途地址，可能存在 Fake-IP DNS 环境。") }
                check.rawData["error"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ResultRow("错误", it) }
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.DOMAIN_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = "域名访问") {
                ResultRow("目标", check.target ?: "未提供")
                ResultRow("结果", check.status.displayName())
                check.rawData["addresses"]
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.forEach { address -> Text("地址：$address") }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.NETWORK_CHANGED }?.let { check ->
            ReportSectionCard(title = "网络切换") {
                Text(check.summary)
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
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CancelledContent(onRunCheck: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("诊断已停止", style = MaterialTheme.typography.titleMedium)
            Text("本次未生成完整报告，也不会写入历史记录。")
            TextButton(onClick = onRunCheck) { Text("重新诊断") }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("诊断无法完成", style = MaterialTheme.typography.titleMedium)
            Text(message)
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun DiagnosticStage.displayName(): String = when (this) {
    DiagnosticStage.NETWORK_CONTEXT -> "获取网络状态"
    DiagnosticStage.GATEWAY -> "检查本地网关"
    DiagnosticStage.PUBLIC_CONNECTIVITY -> "检查公网连接"
    DiagnosticStage.DNS -> "检查 DNS"
    DiagnosticStage.DOMAIN_CONNECTIVITY -> "检查域名访问"
    DiagnosticStage.NETWORK_CHANGED -> "检查网络变化"
    DiagnosticStage.ANALYSIS -> "生成诊断结果"
}

private fun DiagnosticCheck.displayName(): String = when (stage) {
    DiagnosticStage.NETWORK_CONTEXT -> "本机网络"
    DiagnosticStage.GATEWAY -> "本地网关"
    DiagnosticStage.PUBLIC_CONNECTIVITY -> "公网连接"
    DiagnosticStage.DNS -> "DNS 解析"
    DiagnosticStage.DOMAIN_CONNECTIVITY -> "域名访问"
    DiagnosticStage.NETWORK_CHANGED -> "网络变化"
    DiagnosticStage.ANALYSIS -> "诊断分析"
}

@Composable
private fun DiagnosticCheck.displayInfo(): Triple<String, String, Color> = when (status) {
    DiagnosticCheckStatus.PASS -> if (severity == DiagnosticSeverity.HEALTHY) {
        Triple("✓", "正常", severity.color())
    } else {
        Triple("!", "提示", severity.color())
    }

    DiagnosticCheckStatus.FAIL -> Triple(
        if (severity == DiagnosticSeverity.ERROR) "×" else "!",
        severity.displayName(),
        severity.color(),
    )

    DiagnosticCheckStatus.NO_RECORDS -> Triple("!", "无记录", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.NOT_APPLICABLE -> Triple("－", "不适用", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.SKIPPED -> Triple("－", "未执行", DiagnosticSeverity.NOTICE.color())
    DiagnosticCheckStatus.UNKNOWN -> Triple("?", "未确定", MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiagnosticSeverity.displayInfo(): Triple<String, String, Color> = when (this) {
    DiagnosticSeverity.HEALTHY -> Triple("✓", "正常", color())
    DiagnosticSeverity.NOTICE -> Triple("ℹ", "提示", color())
    DiagnosticSeverity.WARNING -> Triple("!", "异常", color())
    DiagnosticSeverity.ERROR -> Triple("×", "严重异常", color())
}

private fun DiagnosticSeverity.displayName(): String = when (this) {
    DiagnosticSeverity.HEALTHY -> "正常"
    DiagnosticSeverity.NOTICE -> "提示"
    DiagnosticSeverity.WARNING -> "异常"
    DiagnosticSeverity.ERROR -> "严重异常"
}

@Composable
private fun DiagnosticSeverity.color(): Color = when (this) {
    DiagnosticSeverity.HEALTHY -> MaterialTheme.colorScheme.primary
    DiagnosticSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    DiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun DiagnosticOverallStatus.displayInfo(): Pair<String, Color> = when (this) {
    DiagnosticOverallStatus.HEALTHY -> "🟢 网络状态正常" to MaterialTheme.colorScheme.primary
    DiagnosticOverallStatus.ATTENTION -> "🟡 发现网络异常" to MaterialTheme.colorScheme.secondary
    DiagnosticOverallStatus.LIMITED -> "🔴 存在严重异常" to MaterialTheme.colorScheme.error
    DiagnosticOverallStatus.UNKNOWN -> "⚪ 状态未确定" to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun DiagnosticCheckStatus.displayName(): String = when (this) {
    DiagnosticCheckStatus.PASS -> "正常"
    DiagnosticCheckStatus.FAIL -> "异常"
    DiagnosticCheckStatus.NO_RECORDS -> "无记录"
    DiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
    DiagnosticCheckStatus.SKIPPED -> "未执行"
    DiagnosticCheckStatus.UNKNOWN -> "未确定"
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.WIFI -> "Wi-Fi"
    ConnectionType.CELLULAR -> "移动网络"
    ConnectionType.ETHERNET -> "以太网"
    ConnectionType.BLUETOOTH -> "蓝牙"
    ConnectionType.VPN -> "VPN"
    ConnectionType.UNKNOWN -> "未知"
}

private fun Boolean?.toEnabledText(): String = when (this) {
    true -> "已启用"
    false -> "未启用"
    null -> "未确定"
}

private fun Boolean?.toYesNoText(): String = when (this) {
    true -> "是"
    false -> "否"
    null -> "未确定"
}

private fun String.methodDisplayName(): String = when (this) {
    "SYSTEM_REACHABILITY" -> "系统可达性检测"
    "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
    "SYSTEM_RESOLVER" -> "系统解析器"
    "TCP_CONNECT_AND_ANDROID_VALIDATED_CONTEXT" -> "TCP 443 与 Android 网络验证"
    "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> "向解析地址建立 TCP 连接"
    else -> this.replace('_', ' ')
}

private fun formatTargetOutcome(outcome: String): String {
    val target = outcome.substringBefore('=').ifBlank { "公网目标" }
    val passed = outcome.substringAfter('=', "").startsWith("PASS")
    return "TCP 目标 $target：${if (passed) "成功" else "未连接"}"
}

private val DISPLAY_STAGES = setOf(
    DiagnosticStage.NETWORK_CONTEXT,
    DiagnosticStage.GATEWAY,
    DiagnosticStage.PUBLIC_CONNECTIVITY,
    DiagnosticStage.DNS,
    DiagnosticStage.DOMAIN_CONNECTIVITY,
)
