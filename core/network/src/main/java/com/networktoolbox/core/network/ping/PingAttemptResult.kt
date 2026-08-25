package com.networktoolbox.core.network.ping

data class PingAttemptResult(
    val target: String,
    val address: String?,
    val protocol: PingProtocol,
    val success: Boolean,
    val latencyMs: Long?,
    val method: PingMethod,
    val errorMessage: String?,
)
