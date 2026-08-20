package com.networktoolbox.core.network.ping

interface PingEngine {
    suspend fun ping(
        target: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): PingResult

    companion object {
        const val DEFAULT_TIMEOUT_MS: Int = 3_000
    }
}
