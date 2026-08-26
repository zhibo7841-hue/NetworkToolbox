package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.tcp.TcpProbeResult

enum class DiagnosticStage {
    NETWORK_CONTEXT,
    GATEWAY,
    PUBLIC_CONNECTIVITY,
    DNS,
    DOMAIN_CONNECTIVITY,
    NETWORK_CHANGED,
    ANALYSIS,
}

enum class DiagnosticStageState {
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
}

enum class DiagnosticCheckStatus {
    PASS,
    FAIL,
    NO_RECORDS,
    NOT_APPLICABLE,
    SKIPPED,
    UNKNOWN,
}

enum class DiagnosticSeverity {
    HEALTHY,
    NOTICE,
    WARNING,
    ERROR,
}

enum class DiagnosticOverallStatus {
    HEALTHY,
    ATTENTION,
    LIMITED,
    UNKNOWN,
}

data class DiagnosticStageProgress(
    val stage: DiagnosticStage,
    val state: DiagnosticStageState,
)

data class DiagnosticCheck(
    val id: String,
    val stage: DiagnosticStage,
    val name: String,
    val status: DiagnosticCheckStatus,
    val severity: DiagnosticSeverity,
    val summary: String,
    val target: String? = null,
    val method: String? = null,
    val observedAt: Long? = null,
    val rawData: Map<String, String> = emptyMap(),
)

data class DiagnosticFindingV2(
    val id: String,
    val severity: DiagnosticSeverity,
    val title: String,
    val description: String,
    val evidenceCheckIds: List<String> = emptyList(),
)

data class DiagnosticRecommendation(
    val priority: Int,
    val title: String,
    val action: String,
    val reason: String? = null,
)

data class DiagnosticProbeTarget(
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    init {
        require(host.isNotBlank()) { "Diagnostic target host must not be blank." }
        require(port in 1..65_535) { "Diagnostic target port must be valid." }
    }

    companion object {
        const val DEFAULT_PORT = 443
    }
}

data class DiagnosticProbeTargets(
    val publicTargets: List<DiagnosticProbeTarget>,
    val domainName: String = DEFAULT_DOMAIN_NAME,
    val domainPort: Int = DEFAULT_PORT,
) {
    init {
        require(domainName.isNotBlank()) { "Diagnostic domain must not be blank." }
        require(domainPort in 1..65_535) { "Diagnostic domain port must be valid." }
    }

    companion object {
        const val DEFAULT_DOMAIN_NAME = "example.com"
        const val DEFAULT_PORT = 443

        /**
         * Public TCP targets are centralized so a single endpoint is never the
         * definition of Internet availability. The list is replaceable in tests
         * and can be reviewed independently from the diagnostic algorithm.
         */
        fun default(): DiagnosticProbeTargets = DiagnosticProbeTargets(
            publicTargets = listOf(
                DiagnosticProbeTarget(host = "1.1.1.1"),
                DiagnosticProbeTarget(host = "8.8.8.8"),
            ),
        )
    }
}

data class DiagnosticPublicTargetResult(
    val target: DiagnosticProbeTarget,
    val result: TcpProbeResult,
    /** False when the adapter could not complete the probe at all. */
    val probeCompleted: Boolean = true,
)

data class DiagnosticPublicConnectivityResult(
    val validated: Boolean?,
    val targetResults: List<DiagnosticPublicTargetResult>,
) {
    val hasSuccessfulTarget: Boolean
        get() = targetResults.any { it.result.success }
}

data class DiagnosticPipelineResult(
    val startedAt: Long,
    val endedAt: Long,
    val networkContext: NetworkContext?,
    val gatewayResult: PingSessionResult?,
    val publicConnectivity: DiagnosticPublicConnectivityResult?,
    val dnsResult: DnsLookupResult?,
    val domainResults: List<TcpProbeResult>,
    val checks: List<DiagnosticCheck>,
    val networkChanged: Boolean,
)

data class DiagnosticReportV2(
    val timestamp: Long,
    val durationMs: Long?,
    val overallStatus: DiagnosticOverallStatus,
    val overallSeverity: DiagnosticSeverity,
    val summary: String,
    val networkSnapshot: NetworkContext?,
    val checks: List<DiagnosticCheck>,
    val findings: List<DiagnosticFindingV2>,
    val recommendations: List<DiagnosticRecommendation>,
)
