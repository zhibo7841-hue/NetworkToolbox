package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.feature.lanscan.domain.LanHostProbe
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Adapts the existing Ping and TCP contracts for one IPv4 LAN host.
 *
 * A failed reachability attempt is deliberately not treated as an offline
 * result. The limited TCP fallback can still provide positive evidence.
 */
class AndroidLanHostProbe(
    private val pingSessionEngine: PingSessionEngine,
    private val tcpPortChecker: TcpPortChecker,
) : LanHostProbe {
    override suspend fun probe(
        ipAddress: String,
        config: LanScanProbeConfig,
    ): LanHostProbeResult {
        currentCoroutineContext().ensureActive()
        val reachability = pingSessionEngine.run(
            request = PingRequest(
                target = ipAddress,
                protocol = PingProtocol.IPV4,
                mode = PingMode.SINGLE,
                count = 1,
                intervalMs = 0,
                timeoutMs = config.reachabilityTimeoutMs,
            ),
        )
        if (reachability.receivedPackets > 0) {
            return LanHostProbeResult(
                ipAddress = ipAddress,
                evidence = listOf(
                    LanDeviceEvidence(
                        method = LanDiscoveryMethod.REACHABILITY,
                        latencyMs = reachability.avgLatencyMs?.roundToLongOrNull(),
                    ),
                ),
            )
        }

        for (port in config.tcpFallbackPorts) {
            currentCoroutineContext().ensureActive()
            val tcpResult = tcpPortChecker.check(
                host = ipAddress,
                port = port,
                timeoutMs = config.tcpTimeoutMs,
            )
            if (tcpResult.success) {
                return LanHostProbeResult(
                    ipAddress = ipAddress,
                    evidence = listOf(
                        LanDeviceEvidence(
                            method = LanDiscoveryMethod.TCP,
                            latencyMs = tcpResult.latencyMs,
                            successfulPort = port,
                            detail = "TCP connection succeeded.",
                        ),
                    ),
                )
            }
        }
        return LanHostProbeResult(ipAddress = ipAddress)
    }
}

private fun Double?.roundToLongOrNull(): Long? = this?.let { value ->
    if (value.isFinite() && value >= 0.0) value.toLong() else null
}
