package com.networktoolbox.core.network.ping

data class PingResult(
    val target: String,
    val success: Boolean,
    val latencyMs: Long?,
    val method: PingMethod,
    val errorMessage: String?,
)
