package com.networktoolbox.core.common.diagnostic

data class DiagnosticReportV1(
    val appVersion: String,
    val timestamp: Long,
    val durationMs: Long?,
    val intent: DiagnosticIntent? = null,
    val target: DiagnosticTarget? = null,
    val networkContextSummary: DiagnosticNetworkSummary? = null,
    val checks: List<DiagnosticCheck> = emptyList(),
    val observations: List<DiagnosticObservation> = emptyList(),
    val findings: List<DiagnosticFinding> = emptyList(),
    val diagnosis: DiagnosticDiagnosis? = null,
    val recommendations: List<DiagnosticRecommendation> = emptyList(),
    val verificationContext: DiagnosticVerificationContext? = null,
    val tracerouteSummary: DiagnosticTracerouteSummary? = null,
    val runStatus: DiagnosticRunStatus = DiagnosticRunStatus.COMPLETED,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported diagnostic report schema version."
        }
        requireBoundedText(appVersion, "app version", 64)
        require(durationMs == null || durationMs >= 0L) { "Duration must not be negative." }
        require(runStatus != DiagnosticRunStatus.RUNNING) {
            "A report cannot represent a running diagnostic."
        }
        require(runStatus != DiagnosticRunStatus.CANCELLED) {
            "A cancelled diagnostic must not be represented as a completed report."
        }
        requireBoundedList(checks.size, "checks", 32)
        requireBoundedList(observations.size, "observations", 64)
        requireBoundedList(findings.size, "findings", 16)
        requireBoundedList(recommendations.size, "recommendations", 16)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
