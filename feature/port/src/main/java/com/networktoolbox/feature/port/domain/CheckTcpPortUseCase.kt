package com.networktoolbox.feature.port.domain

import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import javax.inject.Inject

class CheckTcpPortUseCase @Inject constructor(
    private val tcpPortChecker: TcpPortChecker,
) {
    suspend operator fun invoke(
        host: String,
        portInput: String,
        timeoutMs: Int = TcpPortChecker.DEFAULT_TIMEOUT_MS,
    ): TcpProbeResult {
        val port = portInput.trim().toIntOrNull()
            ?: return TcpProbeResult(
                host = host.trim(),
                port = 0,
                success = false,
                latencyMs = null,
                errorMessage = "Invalid port.",
            )

        return tcpPortChecker.check(host, port, timeoutMs)
    }
}
