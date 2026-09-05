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
import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as AutomaticDiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus as AutomaticDiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticEvidenceLevel
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendation
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity as AutomaticDiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage as AutomaticDiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity
import java.util.Locale
import kotlin.math.round
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
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

                uiState.status is ReportStatus.Completed -> AutomaticReportContent(
                    result = uiState.status.result,
                )

                uiState.status is ReportStatus.NetworkChanged -> NetworkChangedContent(
                    result = uiState.status.result,
                    onRunCheck = onRunCheck,
                )

                uiState.status is ReportStatus.Failed -> FailedContent(
                    status = uiState.status,
                    onRetry = onRunCheck,
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
                Text(
                    if (status is ReportStatus.Success || status is ReportStatus.Completed) {
                        "重新诊断"
                    } else {
                        "开始诊断"
                    },
                )
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
private fun AutomaticReportContent(
    result: AutomaticDiagnosticResult,
) {
    val diagnosis = result.analysis.diagnosis
    var detailsExpanded by rememberSaveable(result.evidence.startedAt) {
        mutableStateOf(false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AutomaticOverview(diagnosis?.status)

        ReportSectionCard(title = "诊断结论") {
            Text(
                text = diagnosis?.explanation ?: "本次检测没有形成可展示的整体结论。",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ReportSectionCard(title = "检查结果") {
            if (result.evidence.checks.isEmpty()) {
                Text("暂无阶段结果。")
            } else {
                diagnosticStages.forEach { stage ->
                    val checks = result.evidence.checks
                        .filter { it.stage == stage }
                        .take(MAX_VISIBLE_CHECKS_PER_STAGE)
                    if (checks.isNotEmpty()) {
                        Text(
                            text = stage.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        checks.forEach { check -> AutomaticCheckRow(check) }
                    }
                }
            }
        }

        ReportSectionCard(title = "发现") {
            if (result.analysis.findings.isEmpty()) {
                Text("未发现需要关注的现象。")
            } else {
                result.analysis.findings
                    .take(MAX_VISIBLE_FINDINGS)
                    .forEach { finding -> AutomaticFindingItem(finding) }
            }
        }

        result.analysis.recommendations
            .take(MAX_VISIBLE_RECOMMENDATIONS)
            .takeIf(List<*>::isNotEmpty)
            ?.let { recommendations ->
                val title = if (diagnosis?.status == DiagnosticDiagnosisStatus.NORMAL) {
                    "如果仍然遇到问题"
                } else {
                    "建议尝试"
                }
                ReportSectionCard(title = title) {
                    recommendations.forEachIndexed { index, recommendation ->
                        Text(
                            text = "${index + 1}. ${recommendation.action}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = recommendation.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            AutomaticDiagnosticDetails(result)
        }
    }
}

@Composable
private fun AutomaticOverview(status: DiagnosticDiagnosisStatus?) {
    val (label, color) = status.overviewDisplayInfo()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("诊断完成", style = MaterialTheme.typography.titleLarge)
            Surface(
                color = color.copy(alpha = 0.14f),
                contentColor = color,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun AutomaticCheckRow(check: AutomaticDiagnosticCheck) {
    val (marker, label, color) = check.displayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$marker ${check.stage.checkDisplayName()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = check.userFacingSummary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutomaticFindingItem(finding: DiagnosticFinding) {
    val (marker, label, color) = finding.severity.findingDisplayInfo()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "$marker $label · ${finding.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Text(finding.description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AutomaticDiagnosticDetails(result: AutomaticDiagnosticResult) {
    val summary = result.evidence.networkContextSummary
    ReportSectionCard(title = "网络环境") {
        if (summary == null) {
            Text("未获得网络环境信息。")
        } else {
            ResultRow("网络类型", summary.connectionType.displayName())
            if (summary.localAddressSummary.isEmpty()) {
                ResultRow("本机地址", "未检测到")
            } else {
                Text(
                    "本机地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summary.localAddressSummary.take(MAX_DETAIL_ADDRESSES).forEach { address ->
                    Text(address, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.prefixLength?.let { ResultRow("IPv4 前缀", "/$it") }
            ResultRow("网关", summary.gateway ?: "未提供")
            ResultRow("VPN", summary.vpnActive.toEnabledText())
            ResultRow("私人 DNS", summary.privateDnsActive.toEnabledText())
            summary.privateDnsServerName?.let { ResultRow("私人 DNS 名称", it) }
            ResultRow("系统联网验证", summary.validated.toValidatedText())
            Text(
                "网络配置 DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.configuredDnsServers.isEmpty()) {
                Text("未配置")
            } else {
                summary.configuredDnsServers.take(MAX_DETAIL_DNS).forEach { server ->
                    Text(server, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    result.evidence.checks
        .filter { it.stage != AutomaticDiagnosticStage.NETWORK_STATE &&
            it.stage != AutomaticDiagnosticStage.IP_CONFIGURATION
        }
        .take(MAX_DETAIL_CHECKS)
        .groupBy { it.stage }
        .forEach { (stage, checks) ->
            ReportSectionCard(title = stage.detailDisplayName()) {
                checks.forEach { check ->
                    ResultRow("结果", check.status.displayName())
                    check.target?.let { target -> ResultRow("目标", target.value) }
                    check.method?.let { method -> ResultRow("检测方式", method.toTechnicalDisplayName()) }
                    Text(
                        check.userFacingSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val observations = result.evidence.observations
                        .filter { it.id in check.evidenceObservationIds }
                    DnsObservationDetails(observations)
                }
            }
        }

    result.analysis.findings
        .filter { it.confidence.name.isNotBlank() }
        .take(MAX_VISIBLE_FINDINGS)
        .let { findings ->
            if (findings.isNotEmpty()) {
                ReportSectionCard(title = "分析依据") {
                    findings.forEach { finding ->
                        Text(
                            "${finding.title} · ${finding.confidence.displayName()} · " +
                                finding.evidenceLevel.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}

@Composable
private fun DnsObservationDetails(observations: List<DiagnosticObservation>) {
    val records = observations.mapNotNull { observation ->
        observation.value as? DiagnosticObservationValue.DnsRecordValue
    }
    if (records.isNotEmpty()) {
        Text("DNS 记录", style = MaterialTheme.typography.labelLarge)
        records.take(MAX_DETAIL_DNS_RECORDS).forEach { record ->
            val suffix = buildString {
                record.ttlSeconds?.let { append(" · TTL $it s") }
                record.priority?.let { append(" · 优先级 $it") }
            }
            Text(
                "${record.recordType}  ${record.value}$suffix",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (observations.any { it.code == DiagnosticObservationCode.FAKE_IP_RANGE_MATCH }) {
        Text(
            "提示：检测到特殊用途地址，可能存在 Fake-IP DNS 环境。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NetworkChangedContent(
    result: AutomaticDiagnosticResult,
    onRunCheck: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("检测过程中网络发生变化", style = MaterialTheme.typography.titleMedium)
            Text(
                result.analysis.diagnosis?.explanation
                    ?: "部分结果可能来自不同网络环境，暂时无法合并判断。",
            )
            Text(
                "建议在网络稳定后重新执行诊断。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRunCheck) { Text("重新诊断") }
        }
    }
}

@Composable
private fun FailedContent(
    status: ReportStatus.Failed,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("诊断无法完成", style = MaterialTheme.typography.titleMedium)
            Text(status.message)
            status.result?.analysis?.diagnosis?.explanation?.let { Text(it) }
            TextButton(onClick = onRetry) { Text("重新诊断") }
        }
    }
}

@Composable
private fun StageProgressRow(
    stage: AutomaticDiagnosticStage,
    status: ReportStageStatus,
) {
    val (marker, label, color) = when (status) {
        ReportStageStatus.COMPLETED -> Triple("✓", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.RUNNING -> Triple("→", stage.displayName(), MaterialTheme.colorScheme.primary)
        ReportStageStatus.FAILED -> Triple("×", stage.displayName(), MaterialTheme.colorScheme.error)
        ReportStageStatus.SKIPPED -> Triple("－", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.NOT_APPLICABLE -> Triple("－", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
        ReportStageStatus.UNKNOWN -> Triple("?", stage.displayName(), MaterialTheme.colorScheme.onSurfaceVariant)
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

        if (report.shouldShowRecommendations()) {
            ReportSectionCard(title = "建议") {
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
                ResultRow("系统联网验证", context.validated.toValidatedText())
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
                if (check.rawData["reason"] == "cellular_gateway_not_applicable") {
                    ResultRow("系统报告网关", check.target ?: "未提供")
                } else {
                    ResultRow("目标", check.target ?: "未提供")
                }
                ResultRow("结果", check.status.displayName())
                check.method?.let { ResultRow("检测方式", it.methodDisplayName()) }
                check.rawData["avgLatencyMs"]?.let {
                    ResultRow("平均延迟", formatMilliseconds(it))
                }
                Text(check.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        report.checks.firstOrNull { it.stage == DiagnosticStage.PUBLIC_CONNECTIVITY }?.let { check ->
            ReportSectionCard(title = "公网") {
                Text("公网探测", style = MaterialTheme.typography.labelLarge)
                check.rawData["targetOutcomes"]
                    ?.split(';')
                    ?.filter(String::isNotBlank)
                    ?.forEach { outcome ->
                        Text(formatTargetOutcome(outcome))
                    }
                check.rawData["domainAccess"]?.let {
                    ResultRow("实际域名访问", it.toStatusDisplayName())
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
                    ?.let { ResultRow("查询耗时", formatMilliseconds(it)) }
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

private const val MAX_VISIBLE_CHECKS_PER_STAGE = 3
private const val MAX_VISIBLE_FINDINGS = 5
private const val MAX_VISIBLE_RECOMMENDATIONS = 3
private const val MAX_DETAIL_CHECKS = 16
private const val MAX_DETAIL_ADDRESSES = 16
private const val MAX_DETAIL_DNS = 16
private const val MAX_DETAIL_DNS_RECORDS = 12

private fun AutomaticDiagnosticStage.displayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "获取网络状态"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "检查 IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "检查本地网关"
    AutomaticDiagnosticStage.INTERNET -> "检查公网连接"
    AutomaticDiagnosticStage.DNS -> "检查 DNS"
    AutomaticDiagnosticStage.TARGET -> "检查目标访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "检查高级路径"
}

private fun AutomaticDiagnosticStage.checkDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "本机网络"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "本地网关"
    AutomaticDiagnosticStage.INTERNET -> "公网连接"
    AutomaticDiagnosticStage.DNS -> "DNS 解析"
    AutomaticDiagnosticStage.TARGET -> "域名访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "高级路径"
}

private fun AutomaticDiagnosticStage.detailDisplayName(): String = when (this) {
    AutomaticDiagnosticStage.NETWORK_STATE -> "网络状态"
    AutomaticDiagnosticStage.IP_CONFIGURATION -> "IP 配置"
    AutomaticDiagnosticStage.GATEWAY -> "网关"
    AutomaticDiagnosticStage.INTERNET -> "公网"
    AutomaticDiagnosticStage.DNS -> "DNS"
    AutomaticDiagnosticStage.TARGET -> "目标访问"
    AutomaticDiagnosticStage.ADVANCED_PATH -> "高级路径"
}

@Composable
private fun AutomaticDiagnosticCheck.displayInfo(): Triple<String, String, Color> = when (status) {
    AutomaticDiagnosticCheckStatus.PASS -> if (severity == AutomaticDiagnosticSeverity.HEALTHY) {
        Triple("✓", "正常", AutomaticDiagnosticSeverity.HEALTHY.color())
    } else {
        Triple("!", "提示", AutomaticDiagnosticSeverity.NOTICE.color())
    }
    AutomaticDiagnosticCheckStatus.FAIL -> if (severity == AutomaticDiagnosticSeverity.ERROR) {
        Triple("×", "严重异常", AutomaticDiagnosticSeverity.ERROR.color())
    } else {
        Triple("!", "异常", AutomaticDiagnosticSeverity.WARNING.color())
    }
    AutomaticDiagnosticCheckStatus.NO_RECORDS ->
        Triple("!", "无记录", AutomaticDiagnosticSeverity.NOTICE.color())
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE ->
        Triple("－", "不适用", MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.SKIPPED ->
        Triple("－", "未执行", MaterialTheme.colorScheme.onSurfaceVariant)
    AutomaticDiagnosticCheckStatus.UNKNOWN ->
        Triple("?", "未确定", MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AutomaticDiagnosticSeverity.color(): Color = when (this) {
    AutomaticDiagnosticSeverity.HEALTHY -> MaterialTheme.colorScheme.primary
    AutomaticDiagnosticSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    AutomaticDiagnosticSeverity.WARNING -> MaterialTheme.colorScheme.secondary
    AutomaticDiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun AutomaticDiagnosticSeverity.findingDisplayInfo(): Triple<String, String, Color> = when (this) {
    AutomaticDiagnosticSeverity.HEALTHY -> Triple("✓", "正常", color())
    AutomaticDiagnosticSeverity.NOTICE -> Triple("ℹ", "提示", color())
    AutomaticDiagnosticSeverity.WARNING -> Triple("!", "异常", color())
    AutomaticDiagnosticSeverity.ERROR -> Triple("×", "严重异常", color())
}

@Composable
private fun DiagnosticDiagnosisStatus?.overviewDisplayInfo(): Pair<String, Color> = when (this) {
    DiagnosticDiagnosisStatus.NORMAL -> "🟢 网络状态正常" to MaterialTheme.colorScheme.primary
    DiagnosticDiagnosisStatus.ATTENTION -> "🟡 发现需要关注的问题" to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.LIMITED -> "🟠 部分网络能力受限" to MaterialTheme.colorScheme.secondary
    DiagnosticDiagnosisStatus.UNKNOWN,
    null,
    -> "⚪ 暂时无法确定网络状态" to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun AutomaticDiagnosticCheckStatus.displayName(): String = when (this) {
    AutomaticDiagnosticCheckStatus.PASS -> "正常"
    AutomaticDiagnosticCheckStatus.FAIL -> "异常"
    AutomaticDiagnosticCheckStatus.NO_RECORDS -> "无记录"
    AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
    AutomaticDiagnosticCheckStatus.SKIPPED -> "未执行"
    AutomaticDiagnosticCheckStatus.UNKNOWN -> "未确定"
}

private fun AutomaticDiagnosticCheck.userFacingSummary(): String = when (stage) {
    AutomaticDiagnosticStage.NETWORK_STATE,
    AutomaticDiagnosticStage.IP_CONFIGURATION,
    -> summary

    AutomaticDiagnosticStage.GATEWAY -> when (status) {
        AutomaticDiagnosticCheckStatus.PASS -> "本地网关可达性探测收到响应。"
        AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用传统本地网关探测。"
        AutomaticDiagnosticCheckStatus.UNKNOWN -> "当前无法确认本地网关是否响应。"
        AutomaticDiagnosticCheckStatus.FAIL -> "本地网关未响应当前探测；这不单独表示网络故障。"
        AutomaticDiagnosticCheckStatus.SKIPPED -> "本地网关探测未执行。"
        AutomaticDiagnosticCheckStatus.NO_RECORDS -> "本地网关没有可用记录。"
    }

    AutomaticDiagnosticStage.INTERNET -> when (status) {
        AutomaticDiagnosticCheckStatus.PASS -> "公网探测收到响应。"
        AutomaticDiagnosticCheckStatus.FAIL -> "公网探测未获得成功响应证据；这不单独证明互联网不可用。"
        AutomaticDiagnosticCheckStatus.UNKNOWN -> "当前无法确认公网连接状态。"
        AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用公网探测。"
        AutomaticDiagnosticCheckStatus.SKIPPED -> "公网探测未执行。"
        AutomaticDiagnosticCheckStatus.NO_RECORDS -> "公网探测没有可用记录。"
    }

    AutomaticDiagnosticStage.DNS -> when (status) {
        AutomaticDiagnosticCheckStatus.PASS -> "DNS 查询完成并返回记录。"
        AutomaticDiagnosticCheckStatus.NO_RECORDS -> "DNS 查询正常完成，但没有返回所请求的记录。"
        AutomaticDiagnosticCheckStatus.FAIL -> "DNS 查询未正常完成。"
        AutomaticDiagnosticCheckStatus.UNKNOWN -> "当前无法确认 DNS 查询结果。"
        AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用 DNS 查询。"
        AutomaticDiagnosticCheckStatus.SKIPPED -> "DNS 查询未执行。"
    }

    AutomaticDiagnosticStage.TARGET -> when (status) {
        AutomaticDiagnosticCheckStatus.PASS -> "目标访问收到成功响应。"
        AutomaticDiagnosticCheckStatus.FAIL -> "目标服务或访问路径未获得成功响应。"
        AutomaticDiagnosticCheckStatus.UNKNOWN -> "当前无法确认目标访问结果。"
        AutomaticDiagnosticCheckStatus.NOT_APPLICABLE -> "当前目标访问不适用。"
        AutomaticDiagnosticCheckStatus.SKIPPED -> "目标访问未执行。"
        AutomaticDiagnosticCheckStatus.NO_RECORDS -> "目标访问没有可用记录。"
    }

    AutomaticDiagnosticStage.ADVANCED_PATH -> "高级路径检查结果已记录。"
}

private fun DiagnosticConnectionType.displayName(): String = when (this) {
    DiagnosticConnectionType.WIFI -> "Wi-Fi"
    DiagnosticConnectionType.CELLULAR -> "移动网络"
    DiagnosticConnectionType.ETHERNET -> "以太网"
    DiagnosticConnectionType.VPN -> "VPN"
    DiagnosticConnectionType.BLUETOOTH -> "蓝牙"
    DiagnosticConnectionType.UNKNOWN -> "未知网络"
}

private fun DiagnosticEvidenceLevel.displayName(): String = when (this) {
    DiagnosticEvidenceLevel.CONFIRMED -> "已确认"
    DiagnosticEvidenceLevel.SUPPORTED -> "有一定依据"
    DiagnosticEvidenceLevel.INCONCLUSIVE -> "证据不足"
    DiagnosticEvidenceLevel.CONTRADICTED -> "存在冲突"
}

private fun com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.displayName(): String = when (this) {
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.HIGH -> "高可信度"
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.MEDIUM -> "中等可信度"
    com.networktoolbox.core.common.diagnostic.DiagnosticConfidence.LOW -> "低可信度"
}

private fun String.toTechnicalDisplayName(): String = when (this) {
    "TCP_CONNECT" -> "TCP 连接探测"
    "SYSTEM_DNS" -> "系统 DNS 解析器"
    "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
    "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> "TCP 443 辅助探测与系统联网状态"
    else -> replace('_', ' ')
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

private fun Boolean?.toValidatedText(): String = when (this) {
    true -> "已通过"
    false -> "未通过"
    null -> "未确定"
}

private fun String.methodDisplayName(): String = when (this) {
    "SYSTEM_REACHABILITY" -> "系统可达性检测"
    "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
    "SYSTEM_RESOLVER" -> "系统解析器"
    "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> "TCP 443 辅助探测与系统联网状态"
    "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> "向解析地址建立 TCP 连接"
    else -> this.replace('_', ' ')
}

private fun formatTargetOutcome(outcome: String): String {
    val target = outcome.substringBefore('=').ifBlank { "公网目标" }
    val passed = outcome.substringAfter('=', "").startsWith("PASS")
    return "$target    ${if (passed) "成功" else "未连接"}"
}

private fun String.toStatusDisplayName(): String = when (this) {
    "PASS" -> "成功"
    "FAIL" -> "未连接"
    "NO_RECORDS" -> "无记录"
    "NOT_APPLICABLE" -> "不适用"
    "SKIPPED" -> "未执行"
    "UNKNOWN" -> "未确定"
    else -> this
}

private fun formatMilliseconds(rawValue: String?): String {
    val value = rawValue
        ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        ?.toDoubleOrNull()
        ?: return "—"
    val rounded = round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toLong()} ms"
    } else {
        "${String.format(Locale.US, "%.1f", rounded)} ms"
    }
}

private fun DiagnosticReportV2.shouldShowRecommendations(): Boolean =
    recommendations.isNotEmpty() && (
            overallSeverity == DiagnosticSeverity.WARNING ||
            overallSeverity == DiagnosticSeverity.ERROR ||
            findings.any {
                it.id == "NETWORK_CHANGED_DURING_RUN" ||
                    it.id == "PUBLIC_CONNECTIVITY_UNCERTAIN"
            }
        )

private val DISPLAY_STAGES = setOf(
    DiagnosticStage.NETWORK_CONTEXT,
    DiagnosticStage.GATEWAY,
    DiagnosticStage.PUBLIC_CONNECTIVITY,
    DiagnosticStage.DNS,
    DiagnosticStage.DOMAIN_CONNECTIVITY,
)
