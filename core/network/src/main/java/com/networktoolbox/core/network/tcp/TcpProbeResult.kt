package com.networktoolbox.core.network.tcp

import com.networktoolbox.core.common.diagnostic.DiagnosticTcpOutcome

data class TcpProbeResult(
    val host: String,
    val port: Int,
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?,
    /** Optional typed outcome; null preserves compatibility with legacy fakes/callers. */
    val outcome: DiagnosticTcpOutcome? = null,
)
