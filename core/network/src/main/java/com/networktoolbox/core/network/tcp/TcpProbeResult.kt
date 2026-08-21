package com.networktoolbox.core.network.tcp

data class TcpProbeResult(
    val host: String,
    val port: Int,
    val success: Boolean,
    val latencyMs: Long?,
    val errorMessage: String?,
)
