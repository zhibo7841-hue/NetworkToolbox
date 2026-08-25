package com.networktoolbox.core.network.ping

data class PingRequest(
    val target: String,
    val protocol: PingProtocol = PingProtocol.AUTO,
    val mode: PingMode = PingMode.SINGLE,
    val count: Int? = 1,
    val intervalMs: Int = DEFAULT_INTERVAL_MS,
    val timeoutMs: Int = PingEngine.DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS: Int = 1_000
    }
}
