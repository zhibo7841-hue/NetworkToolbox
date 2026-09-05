package com.networktoolbox.feature.report.presentation

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticFindingCode
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome
import java.util.Locale

/** A compact, UI-facing representation of one diagnostic stage. */
internal data class DiagnosticStageSummary(
    val stage: DiagnosticStage,
    val status: DiagnosticCheckStatus,
    val severity: DiagnosticSeverity,
    val checks: List<DiagnosticCheck>,
    val summary: String,
)

/**
 * Presentation-only transformations for the v4 report.
 *
 * The evidence and rule output remain unchanged. This mapper only decides how
 * repeated evidence is summarized and how technical values are explained to
 * a person reading the report.
 */
internal object DiagnosticPresentationMapper {
    private val summaryStageOrder = listOf(
        DiagnosticStage.NETWORK_STATE,
        DiagnosticStage.IP_CONFIGURATION,
        DiagnosticStage.GATEWAY,
        DiagnosticStage.INTERNET,
        DiagnosticStage.DNS,
        DiagnosticStage.TARGET,
    )

    fun stageSummaries(checks: List<DiagnosticCheck>): List<DiagnosticStageSummary> =
        summaryStageOrder.mapNotNull { stage ->
            checks
                .filter { it.stage == stage }
                .takeIf { it.isNotEmpty() }
                ?.let { stageChecks -> summarize(stage, stageChecks) }
        }

    fun visibleFindings(findings: List<DiagnosticFinding>): List<DiagnosticFinding> =
        findings.filterNot {
            it.code == DiagnosticFindingCode.NETWORK_APPEARS_NORMAL &&
                it.severity == DiagnosticSeverity.HEALTHY
        }

    fun findingsTitle(findings: List<DiagnosticFinding>): String = when {
        findings.any {
            it.severity == DiagnosticSeverity.WARNING ||
                it.severity == DiagnosticSeverity.ERROR
        } -> "发现的问题"

        findings.any { it.severity == DiagnosticSeverity.NOTICE } -> "网络环境提示"
        else -> "发现"
    }

    fun startCardTitle(completed: Boolean): String =
        if (completed) "再次进行网络诊断" else "开始一次完整诊断"

    fun startCardActionLabel(completed: Boolean): String =
        if (completed) "重新诊断" else "开始诊断"

    fun recommendationSectionTitle(status: DiagnosticDiagnosisStatus?): String =
        if (status == DiagnosticDiagnosisStatus.NORMAL) "如果仍然遇到问题" else "建议尝试"

    fun methodDisplayName(value: String): String = when (value.trim().uppercase(Locale.US)) {
        "SYSTEM_REACHABILITY" -> "系统可达性探测"
        "ICMP" -> "ICMP 探测"
        "UNAVAILABLE" -> "检测方式不可用"
        "TCP_CONNECT" -> "TCP 连接探测"
        "SYSTEM_DNS" -> "系统 DNS 解析器"
        "SYSTEM_RESOLVER" -> "系统解析器"
        "ANDROID_DNS_RESOLVER" -> "Android 系统 DNS 解析器"
        "TCP_443_PROBES_WITH_VALIDATED_CONTEXT" -> "TCP 443 辅助探测与系统联网状态"
        "TCP_CONNECT_TO_RESOLVED_ADDRESS" -> "向解析地址建立 TCP 连接"
        else -> "其他检测方式"
    }

    fun targetDisplayName(check: DiagnosticCheck): String? = check.target?.let { target ->
        if (check.stage == DiagnosticStage.INTERNET || check.stage == DiagnosticStage.TARGET) {
            val host = if (target.value.contains(':')) "[${target.value}]" else target.value
            "$host:${target.port}"
        } else {
            target.value
        }
    }

    fun userFacingSummary(check: DiagnosticCheck): String = when (check.stage) {
        DiagnosticStage.NETWORK_STATE -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "已发现活动网络。"
            DiagnosticCheckStatus.FAIL -> "设备当前没有活动网络。"
            DiagnosticCheckStatus.UNKNOWN -> "当前活动网络状态未能确认。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "本机网络状态不适用。"
            DiagnosticCheckStatus.SKIPPED -> "本机网络状态未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本机网络没有可用记录。"
        }

        DiagnosticStage.IP_CONFIGURATION -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "已观察到本机 IP 地址。"
            DiagnosticCheckStatus.UNKNOWN -> "本机 IP 配置未能确认。"
            DiagnosticCheckStatus.FAIL -> "本机 IP 配置异常；请结合其他阶段结果判断。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "本机 IP 配置不适用。"
            DiagnosticCheckStatus.SKIPPED -> "本机 IP 配置未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本机 IP 配置没有可用记录。"
        }

        DiagnosticStage.GATEWAY -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "本地网关可达性探测收到响应。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用传统本地网关探测。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认本地网关是否响应。"
            DiagnosticCheckStatus.FAIL -> "本地网关未响应当前探测；这不单独表示网络故障。"
            DiagnosticCheckStatus.SKIPPED -> "本地网关探测未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "本地网关没有可用记录。"
        }

        DiagnosticStage.INTERNET -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "公网探测收到响应。"
            DiagnosticCheckStatus.FAIL -> "公网探测未获得成功响应证据；这不单独证明互联网不可用。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认公网连接状态。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用公网探测。"
            DiagnosticCheckStatus.SKIPPED -> "公网探测未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "公网探测没有可用记录。"
        }

        DiagnosticStage.DNS -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "DNS 查询完成并返回记录。"
            DiagnosticCheckStatus.NO_RECORDS -> "DNS 查询正常完成，但没有返回所请求的记录。"
            DiagnosticCheckStatus.FAIL -> "DNS 查询未正常完成。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认 DNS 查询结果。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前网络不适用 DNS 查询。"
            DiagnosticCheckStatus.SKIPPED -> "DNS 查询未执行。"
        }

        DiagnosticStage.TARGET -> when (check.status) {
            DiagnosticCheckStatus.PASS -> "目标访问收到成功响应。"
            DiagnosticCheckStatus.FAIL -> "目标服务或访问路径未获得成功响应。"
            DiagnosticCheckStatus.UNKNOWN -> "当前无法确认目标访问结果。"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "当前目标访问不适用。"
            DiagnosticCheckStatus.SKIPPED -> "目标访问未执行。"
            DiagnosticCheckStatus.NO_RECORDS -> "目标访问没有可用记录。"
        }

        DiagnosticStage.ADVANCED_PATH -> "高级路径检查结果已记录。"
    }

    fun tcpOutcomeDisplayName(outcome: DiagnosticTcpOutcome): String = when (outcome) {
        DiagnosticTcpOutcome.CONNECT_SUCCESS -> "TCP 连接成功"
        DiagnosticTcpOutcome.CONNECTION_REFUSED -> "连接被拒绝"
        DiagnosticTcpOutcome.TIMEOUT -> "连接超时"
        DiagnosticTcpOutcome.NETWORK_UNREACHABLE -> "网络不可达"
        DiagnosticTcpOutcome.NO_ROUTE -> "无可用路由"
        DiagnosticTcpOutcome.UNKNOWN -> "结果未确定"
        DiagnosticTcpOutcome.INTERNAL_ERROR -> "检测内部错误"
    }

    fun tcpOutcomeDisplayName(value: String): String = when (
        value.trim().uppercase(Locale.US)
    ) {
        "PASS" -> "成功"
        "FAIL" -> "未连接"
        "CONNECT_SUCCESS" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.CONNECT_SUCCESS)
        "CONNECTION_REFUSED" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.CONNECTION_REFUSED)
        "TIMEOUT" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.TIMEOUT)
        "NETWORK_UNREACHABLE" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.NETWORK_UNREACHABLE)
        "NO_ROUTE" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.NO_ROUTE)
        "INTERNAL_ERROR" -> tcpOutcomeDisplayName(DiagnosticTcpOutcome.INTERNAL_ERROR)
        else -> "结果未确定"
    }

    fun fakeIpMessage(vpnActive: Boolean?): String = when (vpnActive) {
        true -> "提示：DNS 返回了特殊用途地址；当前诊断也检测到 VPN，结果可能来自 VPN 隧道后的网络环境。"
        false -> "提示：Android 未检测到本机 VPN，但 DNS 返回了特殊用途地址。此类地址也可能由路由器、代理网关或当前网络环境提供。"
        null -> "提示：当前未确认本机 VPN 状态，但 DNS 返回了特殊用途地址。此类地址也可能由路由器、代理网关或当前网络环境提供。"
    }

    private fun summarize(
        stage: DiagnosticStage,
        checks: List<DiagnosticCheck>,
    ): DiagnosticStageSummary {
        val status = aggregateStatus(checks)
        val severity = aggregateSeverity(checks)
        val summary = if (stage == DiagnosticStage.INTERNET) {
            publicSummary(checks)
        } else {
            val summaries = checks.map(::userFacingSummary).distinct()
            when {
                summaries.size == 1 -> summaries.single()
                checks.any { it.status == DiagnosticCheckStatus.PASS } ->
                    "部分${stage.displayName()}结果存在差异；详细结果请查看详细信息。"
                else -> "${stage.displayName()}结果存在差异；详细结果请查看详细信息。"
            }
        }
        return DiagnosticStageSummary(
            stage = stage,
            status = status,
            severity = severity,
            checks = checks,
            summary = summary,
        )
    }

    private fun publicSummary(checks: List<DiagnosticCheck>): String {
        val successful = checks.count { it.status == DiagnosticCheckStatus.PASS }
        return when {
            successful == checks.size -> "$successful 个公网探测目标均获得有效响应。"
            successful > 0 -> "部分探测结果存在差异；$successful 个公网探测目标获得有效响应，其余结果请查看详细信息。"
            checks.all { it.status == DiagnosticCheckStatus.UNKNOWN } -> "公网探测结果未能确认。"
            else -> "公网探测未获得成功响应证据；详细结果请查看详细信息。"
        }
    }

    private fun aggregateStatus(checks: List<DiagnosticCheck>): DiagnosticCheckStatus = when {
        checks.all { it.status == DiagnosticCheckStatus.PASS } -> DiagnosticCheckStatus.PASS
        checks.any { it.status == DiagnosticCheckStatus.PASS } -> DiagnosticCheckStatus.PASS
        checks.all { it.status == DiagnosticCheckStatus.NO_RECORDS } -> DiagnosticCheckStatus.NO_RECORDS
        checks.all { it.status == DiagnosticCheckStatus.NOT_APPLICABLE } -> DiagnosticCheckStatus.NOT_APPLICABLE
        checks.all { it.status == DiagnosticCheckStatus.SKIPPED } -> DiagnosticCheckStatus.SKIPPED
        checks.any { it.status == DiagnosticCheckStatus.FAIL } -> DiagnosticCheckStatus.FAIL
        else -> DiagnosticCheckStatus.UNKNOWN
    }

    private fun aggregateSeverity(checks: List<DiagnosticCheck>): DiagnosticSeverity {
        val hasSuccess = checks.any { it.status == DiagnosticCheckStatus.PASS }
        val hasOther = checks.any { it.status != DiagnosticCheckStatus.PASS }
        if (hasSuccess && hasOther) return DiagnosticSeverity.NOTICE
        return checks.maxByOrNull { it.severity.rank() }?.severity ?: DiagnosticSeverity.NOTICE
    }

    private fun DiagnosticStage.displayName(): String = when (this) {
        DiagnosticStage.NETWORK_STATE -> "本机网络"
        DiagnosticStage.IP_CONFIGURATION -> "IP 配置"
        DiagnosticStage.GATEWAY -> "本地网关"
        DiagnosticStage.INTERNET -> "公网连接"
        DiagnosticStage.DNS -> "DNS 解析"
        DiagnosticStage.TARGET -> "目标访问"
        DiagnosticStage.ADVANCED_PATH -> "高级路径"
    }

    private fun DiagnosticSeverity.rank(): Int = when (this) {
        DiagnosticSeverity.HEALTHY -> 0
        DiagnosticSeverity.NOTICE -> 1
        DiagnosticSeverity.WARNING -> 2
        DiagnosticSeverity.ERROR -> 3
    }
}
