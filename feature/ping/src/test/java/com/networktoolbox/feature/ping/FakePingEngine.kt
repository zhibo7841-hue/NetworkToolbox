package com.networktoolbox.feature.ping

import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.PingResult

class FakePingEngine(
    private val response: PingResult,
) : PingEngine {
    var callCount: Int = 0
        private set
    var receivedTarget: String? = null
        private set
    var receivedTimeoutMs: Int? = null
        private set

    override suspend fun ping(target: String, timeoutMs: Int): PingResult {
        callCount += 1
        receivedTarget = target
        receivedTimeoutMs = timeoutMs
        return response
    }
}
