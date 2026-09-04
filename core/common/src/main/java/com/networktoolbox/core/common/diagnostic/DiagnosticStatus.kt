package com.networktoolbox.core.common.diagnostic

enum class DiagnosticRunStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    NETWORK_CHANGED,
    FAILED,
}
enum class DiagnosticDiagnosisStatus {
    NORMAL,
    ATTENTION,
    LIMITED,
    UNKNOWN,
}

enum class DiagnosticSeverity {
    HEALTHY,
    NOTICE,
    WARNING,
    ERROR,
}

enum class DiagnosticConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class DiagnosticEvidenceLevel {
    CONFIRMED,
    SUPPORTED,
    INCONCLUSIVE,
    CONTRADICTED,
}

enum class DiagnosticObservationState {
    CONFIRMED,
    UNAVAILABLE,
    UNKNOWN,
}

/** Domain stages only; report assembly is not a fake diagnostic stage. */
enum class DiagnosticStage {
    NETWORK_STATE,
    IP_CONFIGURATION,
    GATEWAY,
    INTERNET,
    DNS,
    TARGET,
    ADVANCED_PATH,
}

enum class DiagnosticCheckStatus {
    PASS,
    FAIL,
    NO_RECORDS,
    NOT_APPLICABLE,
    SKIPPED,
    UNKNOWN,
}

/** A TCP outcome is evidence for a check, not a replacement for CheckStatus. */
enum class DiagnosticTcpOutcome {
    CONNECT_SUCCESS,
    CONNECTION_REFUSED,
    TIMEOUT,
    NETWORK_UNREACHABLE,
    NO_ROUTE,
    UNKNOWN,
    INTERNAL_ERROR,
}

enum class DiagnosticDnsOutcome {
    SUCCESS,
    PARTIAL,
    NO_RECORDS,
    NXDOMAIN,
    TIMEOUT,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    UNKNOWN,
}

enum class DiagnosticAddressFamily {
    IPV4,
    IPV6,
    UNKNOWN,
}

enum class DiagnosticRecommendationPriority {
    PRIMARY,
    SECONDARY,
    OPTIONAL,
}

enum class DiagnosticConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    UNKNOWN,
}

enum class DiagnosticTargetKind {
    DOMAIN,
    IPV4,
    IPV6,
}

enum class DiagnosticProblemType {
    GENERAL,
    NO_INTERNET,
    TARGET_NOT_ACCESSIBLE,
    SLOW_OR_UNSTABLE,
    LAN_DEVICE_UNREACHABLE,
}

enum class DiagnosticComparisonStatus {
    NOT_COMPARED,
    PREVIOUS_FINDINGS_GONE,
    FINDINGS_PERSIST,
    EVIDENCE_CHANGED,
}

enum class DiagnosticTracerouteStatus {
    NOT_RUN,
    REACHED,
    PARTIAL,
    FAILED,
    CANCELLED,
    NETWORK_CHANGED,
}

enum class DiagnosticReportViewLevel {
    CONCISE,
    TECHNICAL,
}
