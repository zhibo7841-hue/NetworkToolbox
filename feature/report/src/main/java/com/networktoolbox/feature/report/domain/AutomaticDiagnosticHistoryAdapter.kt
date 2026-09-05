package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.diagnostic.DiagnosticCheck as V4Check
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckCode
import com.networktoolbox.core.common.diagnostic.DiagnosticCheckStatus as V4CheckStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticConnectionType
import com.networktoolbox.core.common.diagnostic.DiagnosticFinding
import com.networktoolbox.core.common.diagnostic.DiagnosticNetworkSummary
import com.networktoolbox.core.common.diagnostic.DiagnosticObservation
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationCode
import com.networktoolbox.core.common.diagnostic.DiagnosticObservationValue
import com.networktoolbox.core.common.diagnostic.DiagnosticRecommendationPriority
import com.networktoolbox.core.common.diagnostic.DiagnosticSeverity as V4Severity
import com.networktoolbox.core.common.diagnostic.DiagnosticStage as V4Stage
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheck
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticCheckStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticFindingV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticOverallStatus
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2HistorySerializer
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticSeverity
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence

/**
 * Projects the v4 result into the existing schema-2 report envelope.
 *
 * This is deliberately an adapter rather than a second persistence path: the
 * generic local history table and the existing report-history viewer remain
 * unchanged while the live screen consumes the richer v4 result directly.
 */
internal object AutomaticDiagnosticHistoryAdapter {
    fun toHistoryRecord(
        evidence: DiagnosticRunEvidence,
        analysis: DiagnosticAnalysisResult,
    ): HistoryRecord {
        val diagnosis = analysis.diagnosis
        val legacyChecks = evidence.checks.mapIndexed { index, check ->
            check.toLegacy(
                dnsOccurrence = evidence.checks
                    .take(index)
                    .count { previous -> previous.code == DiagnosticCheckCode.DNS_RESOLUTION },
            )
        }
        val legacyReport = DiagnosticReportV2(
            timestamp = evidence.startedAt,
            durationMs = evidence.durationMs,
            overallStatus = diagnosis?.status.toLegacyStatus(),
            overallSeverity = analysis.findings.maxByOrNull { it.severity.rank() }
                ?.severity
                .toLegacySeverityOrNotice(),
            summary = diagnosis?.title ?: "诊断结果未确定。",
            networkSnapshot = evidence.networkContextSummary?.toNetworkContext(evidence),
            checks = legacyChecks,
            findings = analysis.findings.map { it.toLegacyFinding(legacyChecks) },
            recommendations = analysis.recommendations.map { recommendation ->
                com.networktoolbox.feature.report.diagnostic.v2.DiagnosticRecommendation(
                    priority = recommendation.priority.toLegacyPriority(),
                    title = recommendation.title,
                    action = recommendation.action,
                    reason = recommendation.reason,
                )
            },
        )
        return DiagnosticReportV2HistorySerializer.toHistoryRecord(legacyReport)
    }

    private fun V4Check.toLegacy(dnsOccurrence: Int): DiagnosticCheck = DiagnosticCheck(
        id = legacyCheckId(dnsOccurrence),
        stage = stage.toLegacyStage(),
        name = stage.displayName(),
        status = status.toLegacyStatus(),
        severity = severity.toLegacySeverity(),
        summary = summary,
        target = target?.value,
        method = method,
        observedAt = observedAt,
    )

    private fun V4Check.legacyCheckId(dnsOccurrence: Int): String = when (code) {
        DiagnosticCheckCode.NETWORK_STATE -> "NETWORK_CONTEXT"
        DiagnosticCheckCode.IP_CONFIGURATION -> "IP_CONFIGURATION"
        DiagnosticCheckCode.GATEWAY -> "GATEWAY_REACHABILITY"
        DiagnosticCheckCode.PUBLIC_CONNECTIVITY -> "PUBLIC_CONNECTIVITY"
        DiagnosticCheckCode.DNS_RESOLUTION -> if (dnsOccurrence == 0) {
            "DNS_RESOLUTION"
        } else {
            "DNS_RESOLUTION_$dnsOccurrence"
        }
        DiagnosticCheckCode.TARGET_CONNECTIVITY -> "DOMAIN_ACCESS"
        DiagnosticCheckCode.NETWORK_STABILITY -> "NETWORK_CHANGED"
        DiagnosticCheckCode.ADVANCED_PATH -> "ADVANCED_PATH"
    }

    private fun DiagnosticFinding.toLegacyFinding(
        legacyChecks: List<DiagnosticCheck>,
    ): DiagnosticFindingV2 = DiagnosticFindingV2(
        id = code.name,
        severity = severity.toLegacySeverity(),
        title = title,
        description = description,
        evidenceCheckIds = evidenceCheckCodes.mapNotNull { code ->
            legacyChecks.firstOrNull { it.id == code.legacyId() }?.id
                ?: code.legacyId()
        }.distinct(),
    )

    private fun DiagnosticCheckCode.legacyId(): String = when (this) {
        DiagnosticCheckCode.NETWORK_STATE -> "NETWORK_CONTEXT"
        DiagnosticCheckCode.IP_CONFIGURATION -> "IP_CONFIGURATION"
        DiagnosticCheckCode.GATEWAY -> "GATEWAY_REACHABILITY"
        DiagnosticCheckCode.PUBLIC_CONNECTIVITY -> "PUBLIC_CONNECTIVITY"
        DiagnosticCheckCode.DNS_RESOLUTION -> "DNS_RESOLUTION"
        DiagnosticCheckCode.TARGET_CONNECTIVITY -> "DOMAIN_ACCESS"
        DiagnosticCheckCode.NETWORK_STABILITY -> "NETWORK_CHANGED"
        DiagnosticCheckCode.ADVANCED_PATH -> "ADVANCED_PATH"
    }

    private fun V4Stage.toLegacyStage(): DiagnosticStage = when (this) {
        V4Stage.NETWORK_STATE -> DiagnosticStage.NETWORK_CONTEXT
        V4Stage.IP_CONFIGURATION -> DiagnosticStage.NETWORK_CONTEXT
        V4Stage.GATEWAY -> DiagnosticStage.GATEWAY
        V4Stage.INTERNET -> DiagnosticStage.PUBLIC_CONNECTIVITY
        V4Stage.DNS -> DiagnosticStage.DNS
        V4Stage.TARGET -> DiagnosticStage.DOMAIN_CONNECTIVITY
        V4Stage.ADVANCED_PATH -> DiagnosticStage.ANALYSIS
    }

    private fun V4Stage.displayName(): String = when (this) {
        V4Stage.NETWORK_STATE -> "本机网络"
        V4Stage.IP_CONFIGURATION -> "IP 配置"
        V4Stage.GATEWAY -> "本地网关"
        V4Stage.INTERNET -> "公网连接"
        V4Stage.DNS -> "DNS 解析"
        V4Stage.TARGET -> "目标访问"
        V4Stage.ADVANCED_PATH -> "高级路径"
    }

    private fun V4CheckStatus.toLegacyStatus(): DiagnosticCheckStatus = when (this) {
        V4CheckStatus.PASS -> DiagnosticCheckStatus.PASS
        V4CheckStatus.FAIL -> DiagnosticCheckStatus.FAIL
        V4CheckStatus.NO_RECORDS -> DiagnosticCheckStatus.NO_RECORDS
        V4CheckStatus.NOT_APPLICABLE -> DiagnosticCheckStatus.NOT_APPLICABLE
        V4CheckStatus.SKIPPED -> DiagnosticCheckStatus.SKIPPED
        V4CheckStatus.UNKNOWN -> DiagnosticCheckStatus.UNKNOWN
    }

    private fun V4Severity.toLegacySeverity(): DiagnosticSeverity = when (this) {
        V4Severity.HEALTHY -> DiagnosticSeverity.HEALTHY
        V4Severity.NOTICE -> DiagnosticSeverity.NOTICE
        V4Severity.WARNING -> DiagnosticSeverity.WARNING
        V4Severity.ERROR -> DiagnosticSeverity.ERROR
    }

    private fun V4Severity?.toLegacySeverityOrNotice(): DiagnosticSeverity =
        this?.toLegacySeverity() ?: DiagnosticSeverity.NOTICE

    private fun com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus?
        .toLegacyStatus(): DiagnosticOverallStatus = when (this) {
        com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.NORMAL ->
            DiagnosticOverallStatus.HEALTHY
        com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.ATTENTION ->
            DiagnosticOverallStatus.ATTENTION
        com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.LIMITED ->
            DiagnosticOverallStatus.LIMITED
        com.networktoolbox.core.common.diagnostic.DiagnosticDiagnosisStatus.UNKNOWN,
        null,
        -> DiagnosticOverallStatus.UNKNOWN
    }

    private fun DiagnosticRecommendationPriority.toLegacyPriority(): Int = when (this) {
        DiagnosticRecommendationPriority.PRIMARY -> 1
        DiagnosticRecommendationPriority.SECONDARY -> 2
        DiagnosticRecommendationPriority.OPTIONAL -> 3
    }

    private fun V4Severity.rank(): Int = when (this) {
        V4Severity.HEALTHY -> 0
        V4Severity.NOTICE -> 1
        V4Severity.WARNING -> 2
        V4Severity.ERROR -> 3
    }

    private fun DiagnosticNetworkSummary.toNetworkContext(
        evidence: DiagnosticRunEvidence,
    ): NetworkContext = NetworkContext(
        connectionType = connectionType.toConnectionType(),
        ipv4Address = localAddressSummary.firstOrNull { it.contains('.') },
        ipv6Address = localAddressSummary.firstOrNull { it.contains(':') },
        gateway = gateway,
        dnsServers = configuredDnsServers,
        vpnActive = vpnActive,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = evidence.observations.activeNetworkValue(),
        validated = validated,
        ipv6Addresses = localAddressSummary.filter { it.contains(':') },
        ipv4PrefixLength = prefixLength?.takeIf { it in 0..32 },
    )

    private fun DiagnosticConnectionType.toConnectionType(): ConnectionType = when (this) {
        DiagnosticConnectionType.WIFI -> ConnectionType.WIFI
        DiagnosticConnectionType.CELLULAR -> ConnectionType.CELLULAR
        DiagnosticConnectionType.ETHERNET -> ConnectionType.ETHERNET
        DiagnosticConnectionType.VPN -> ConnectionType.VPN
        DiagnosticConnectionType.BLUETOOTH -> ConnectionType.BLUETOOTH
        DiagnosticConnectionType.UNKNOWN -> ConnectionType.UNKNOWN
    }

    private fun List<DiagnosticObservation>.activeNetworkValue(): Boolean? = firstOrNull {
        it.code == DiagnosticObservationCode.ACTIVE_NETWORK_AVAILABLE
    }?.let { observation ->
        (observation.value as? DiagnosticObservationValue.BooleanValue)?.value
    }
}
