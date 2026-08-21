package com.networktoolbox.core.network.tcp

interface TcpPortChecker {
    suspend fun check(
        host: String,
        port: Int,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): TcpProbeResult

    companion object {
        const val DEFAULT_TIMEOUT_MS: Int = 3_000
    }
}
