package com.networktoolbox.feature.ping

import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionProgress
import com.networktoolbox.core.network.ping.PingSessionResult
import kotlinx.coroutines.awaitCancellation

class FakePingSessionEngine(
    private val response: PingSessionResult? = null,
    private val waitForCancellation: Boolean = false,
    private val progressBeforeWaiting: PingSessionProgress? = null,
) : PingSessionEngine {
    var callCount: Int = 0
        private set

    var receivedRequest: PingRequest? = null
        private set

    override suspend fun run(
        request: PingRequest,
        onProgress: (PingSessionProgress) -> Unit,
    ): PingSessionResult {
        callCount += 1
        receivedRequest = request
        progressBeforeWaiting?.let(onProgress)
        if (waitForCancellation) {
            awaitCancellation()
        }
        response?.let { result ->
            onProgress(
                PingSessionProgress(
                    target = result.target,
                    sentPackets = result.sentPackets,
                    receivedPackets = result.receivedPackets,
                    lostPackets = result.lostPackets,
                    packetLoss = result.packetLoss,
                    latestLatencyMs = result.maxLatencyMs,
                    minLatencyMs = result.minLatencyMs,
                    avgLatencyMs = result.avgLatencyMs,
                    maxLatencyMs = result.maxLatencyMs,
                ),
            )
        }
        return requireNotNull(response)
    }
}
