package com.networktoolbox.feature.port

import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult

class FakeTcpPortChecker(
    private val response: TcpProbeResult,
) : TcpPortChecker {
    var callCount: Int = 0
        private set
    var receivedHost: String? = null
        private set
    var receivedPort: Int? = null
        private set
    var receivedTimeoutMs: Int? = null
        private set

    override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
        callCount += 1
        receivedHost = host
        receivedPort = port
        receivedTimeoutMs = timeoutMs
        return response
    }
}
