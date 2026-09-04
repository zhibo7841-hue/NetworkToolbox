package com.networktoolbox.core.common.diagnostic

data class DiagnosticTarget(
    val value: String,
    val kind: DiagnosticTargetKind,
    val port: Int = DEFAULT_PORT,
) {
    init {
        requireBoundedText(value, "target", 256)
        require(port in 1..65_535) { "Target port must be valid." }
    }

    companion object {
        const val DEFAULT_PORT = 443
    }
}
/** General is the default; problem selection and target are optional inputs. */
data class DiagnosticIntent(
    val problemType: DiagnosticProblemType = DiagnosticProblemType.GENERAL,
    val target: DiagnosticTarget? = null,
)
