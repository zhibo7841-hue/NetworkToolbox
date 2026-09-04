package com.networktoolbox.core.common.diagnostic

enum class DiagnosticObservationSource {
    NETWORK_REPOSITORY,
    LINK_PROPERTIES,
    NETWORK_CAPABILITIES,
    PING_ENGINE,
    DNS_ENGINE,
    TCP_CHECKER,
    TRACEROUTE_ENGINE,
    USER_INPUT,
}
data class DiagnosticObservation(
    val id: String,
    val code: DiagnosticObservationCode,
    val stage: DiagnosticStage,
    val source: DiagnosticObservationSource,
    val value: DiagnosticObservationValue,
    val observedAt: Long,
    val networkFingerprint: NetworkFingerprint? = null,
    val evidenceState: DiagnosticObservationState = DiagnosticObservationState.CONFIRMED,
) {
    init {
        requireBoundedText(id, "observation id", 128)
    }
}
