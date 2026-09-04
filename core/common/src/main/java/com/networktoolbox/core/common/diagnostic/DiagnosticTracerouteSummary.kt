package com.networktoolbox.core.common.diagnostic

data class DiagnosticTracerouteSummary(
    val target: DiagnosticTarget,
    val resolvedAddress: String? = null,
    val status: DiagnosticTracerouteStatus,
    val hopCount: Int,
    val respondedHopCount: Int,
    val reachedTarget: Boolean,
    val durationMs: Long? = null,
) {
    init {
        resolvedAddress?.let { requireBoundedText(it, "resolved address", 128) }
        require(hopCount >= 0) { "Hop count must not be negative." }
        require(respondedHopCount in 0..hopCount) {
            "Responded hop count must be within hop count."
        }
        require(durationMs == null || durationMs >= 0L) { "Duration must not be negative." }
    }
}
