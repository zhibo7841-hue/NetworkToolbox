package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.lanscan.domain.LanHostProbe
import com.networktoolbox.feature.lanscan.domain.LanHostProbeTraceProvider
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeTrace
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanReachabilityOutcome
import com.networktoolbox.feature.lanscan.domain.model.LanReachabilityTrace
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanTcpOutcome
import com.networktoolbox.feature.lanscan.domain.model.LanTcpProbeTrace
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
) : LanHostProbe, LanHostProbeTraceProvider {
    override suspend fun probe(
        ipAddress: String,
        config: LanScanProbeConfig,
    ): LanHostProbeResult = probeWithTrace(ipAddress, config).toProbeResult()

    override suspend fun probeWithTrace(
        ipAddress: String,
        config: LanScanProbeConfig,
    ): LanHostProbeTrace {
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
        val reachabilityTrace = reachability.toLanTrace()
        if (reachabilityTrace.outcome == LanReachabilityOutcome.SUCCESS) {
            return LanHostProbeTrace(
                ipAddress = ipAddress,
                reachability = reachabilityTrace,
                discovered = true,
                discoveryMethod = LanDiscoveryMethod.REACHABILITY,
            )
        }

        val tcpProbes = mutableListOf<LanTcpProbeTrace>()
        for (port in config.tcpFallbackPorts) {
            currentCoroutineContext().ensureActive()
            val tcpResult = tcpPortChecker.check(
                host = ipAddress,
                port = port,
                timeoutMs = config.tcpTimeoutMs,
            )
            val tcpTrace = tcpResult.toLanTrace()
            tcpProbes += tcpTrace
            if (tcpResult.success) {
                return LanHostProbeTrace(
                    ipAddress = ipAddress,
                    reachability = reachabilityTrace,
                    tcpProbes = tcpProbes,
                    discovered = true,
                    discoveryMethod = LanDiscoveryMethod.TCP,
                    successfulPort = port,
                )
            }
        }
        return LanHostProbeTrace(
            ipAddress = ipAddress,
            reachability = reachabilityTrace,
            tcpProbes = tcpProbes,
            discovered = false,
        )
    }
}

private fun LanHostProbeTrace.toProbeResult(): LanHostProbeResult {
    val evidence = when (discoveryMethod) {
        LanDiscoveryMethod.REACHABILITY -> listOf(
            LanDeviceEvidence(
                method = LanDiscoveryMethod.REACHABILITY,
                latencyMs = reachability.latencyMs,
            ),
        )

        LanDiscoveryMethod.TCP -> listOf(
            LanDeviceEvidence(
                method = LanDiscoveryMethod.TCP,
                latencyMs = tcpProbes.lastOrNull { it.outcome == LanTcpOutcome.OPEN }?.latencyMs,
                successfulPort = successfulPort,
                detail = "TCP connection succeeded.",
            ),
        )

        null,
        LanDiscoveryMethod.LOCAL_CONTEXT,
        LanDiscoveryMethod.GATEWAY_CONTEXT,
        -> emptyList()
    }
    return LanHostProbeResult(ipAddress = ipAddress, evidence = evidence)
}

private fun PingSessionResult.toLanTrace(): LanReachabilityTrace {
    if (receivedPackets > 0) {
        return LanReachabilityTrace(
            outcome = LanReachabilityOutcome.SUCCESS,
            latencyMs = avgLatencyMs?.roundToLongOrNull(),
        )
    }
    val message = errorMessage?.takeIf(String::isNotBlank)
    return LanReachabilityTrace(
        outcome = if (message.containsIgnoreCase("timeout")) {
            LanReachabilityOutcome.TIMEOUT
        } else {
            LanReachabilityOutcome.FAILURE
        },
        errorMessage = message,
    )
}

private fun TcpProbeResult.toLanTrace(): LanTcpProbeTrace = LanTcpProbeTrace(
    port = port,
    outcome = when {
        success -> LanTcpOutcome.OPEN
        errorMessage.containsIgnoreCase("refused") -> LanTcpOutcome.REFUSED
        errorMessage.containsIgnoreCase("timeout") -> LanTcpOutcome.TIMEOUT
        errorMessage.containsIgnoreCase("unreachable") -> LanTcpOutcome.UNREACHABLE
        else -> LanTcpOutcome.FAILURE
    },
    latencyMs = latencyMs,
    errorMessage = errorMessage?.takeIf(String::isNotBlank),
)

private fun String?.containsIgnoreCase(value: String): Boolean =
    this?.contains(value, ignoreCase = true) == true

private fun Double?.roundToLongOrNull(): Long? = this?.let { value ->
    if (value.isFinite() && value >= 0.0) value.toLong() else null
}
