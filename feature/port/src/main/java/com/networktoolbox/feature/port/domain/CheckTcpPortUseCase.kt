package com.networktoolbox.feature.port.domain

import com.networktoolbox.core.common.history.HistoryRecordFactory
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import javax.inject.Inject

class CheckTcpPortUseCase @Inject constructor(
    private val tcpPortChecker: TcpPortChecker,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        host: String,
        portInput: String,
        timeoutMs: Int = TcpPortChecker.DEFAULT_TIMEOUT_MS,
        persistHistory: Boolean = true,
    ): TcpProbeResult {
        val port = portInput.trim().toIntOrNull()
        if (port == null) {
            val result = TcpProbeResult(
                host = host.trim(),
                port = 0,
                success = false,
                latencyMs = null,
                errorMessage = "Invalid port.",
            )
            if (persistHistory) {
                recordHistory(result)
            }
            return result
        }

        return tcpPortChecker.check(host, port, timeoutMs)
            .also { result ->
                if (persistHistory) {
                    recordHistory(result)
                }
            }
    }

    private suspend fun recordHistory(result: TcpProbeResult) {
        historyRecorder.record(
            HistoryRecordFactory.tcp(
                timestamp = System.currentTimeMillis(),
                host = result.host,
                port = result.port,
                success = result.success,
                latencyMs = result.latencyMs,
                errorMessage = result.errorMessage,
            ),
        )
    }
}
