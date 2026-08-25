package com.networktoolbox.feature.ping

import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionResult
import kotlinx.coroutines.awaitCancellation

class FakePingSessionEngine(
    private val response: PingSessionResult? = null,
    private val waitForCancellation: Boolean = false,
) : PingSessionEngine {
    var callCount: Int = 0
        private set

    var receivedRequest: PingRequest? = null
        private set

    override suspend fun run(request: PingRequest): PingSessionResult {
        callCount += 1
        receivedRequest = request
        if (waitForCancellation) {
            awaitCancellation()
        }
        return requireNotNull(response)
    }
}
