package com.networktoolbox.feature.ping.domain

import com.networktoolbox.core.common.history.HistoryRecordFactory
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionProgress
import com.networktoolbox.core.network.ping.PingSessionResult
import javax.inject.Inject

class ExecutePingSessionUseCase @Inject constructor(
    private val pingSessionEngine: PingSessionEngine,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        request: PingRequest,
        persistHistory: Boolean = true,
        onProgress: (PingSessionProgress) -> Unit = {},
    ): PingSessionResult = pingSessionEngine.run(request, onProgress).also { result ->
        if (persistHistory) {
            historyRecorder.record(
                HistoryRecordFactory.pingSession(
                    timestamp = System.currentTimeMillis(),
                    target = result.target,
                    address = result.address,
                    protocol = result.protocol.name,
                    mode = result.mode.name,
                    startTime = result.startTime,
                    endTime = result.endTime,
                    sentPackets = result.sentPackets,
                    receivedPackets = result.receivedPackets,
                    lostPackets = result.lostPackets,
                    packetLoss = result.packetLoss,
                    minLatencyMs = result.minLatencyMs,
                    avgLatencyMs = result.avgLatencyMs,
                    maxLatencyMs = result.maxLatencyMs,
                    jitterMs = result.jitterMs,
                    qualityLevel = result.qualityLevel.name,
                    method = result.method.name,
                    summary = result.summary,
                    errorMessage = result.errorMessage,
                ),
            )
        }
    }
}
