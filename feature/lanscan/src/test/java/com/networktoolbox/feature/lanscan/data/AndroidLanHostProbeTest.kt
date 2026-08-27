package com.networktoolbox.feature.lanscan.data

import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLanHostProbeTest {
    @Test
    fun `reachability success skips tcp fallback`() = kotlinx.coroutines.test.runTest {
        val checkedPorts = CopyOnWriteArrayList<Int>()
        val probe = AndroidLanHostProbe(
            pingSessionEngine = FakePingSessionEngine(pingResult(success = true)),
            tcpPortChecker = FakeTcpPortChecker(checkedPorts),
        )

        val result = probe.probe("192.168.1.10", LanScanProbeConfig())

        assertEquals(listOf(LanDiscoveryMethod.REACHABILITY), result.evidence.map { it.method })
        assertEquals(0, checkedPorts.size)
        assertEquals(12L, result.latencyMs)
    }

    @Test
    fun `reachability failure falls back to tcp 445`() = kotlinx.coroutines.test.runTest {
        val checkedPorts = CopyOnWriteArrayList<Int>()
        val probe = AndroidLanHostProbe(
            pingSessionEngine = FakePingSessionEngine(pingResult(success = false)),
            tcpPortChecker = FakeTcpPortChecker(
                checkedPorts = checkedPorts,
                successful = { port -> port == 445 },
            ),
        )

        val result = probe.probe("192.168.1.10", LanScanProbeConfig())

        assertEquals(listOf(80, 443, 22, 445), checkedPorts)
        assertEquals(LanDiscoveryMethod.TCP, result.evidence.single().method)
        assertEquals(445, result.evidence.single().successfulPort)
        assertTrue(result.hasPositiveEvidence)
    }

    @Test
    fun `reachability failure can discover a printer on tcp 9100`() =
        kotlinx.coroutines.test.runTest {
            val checkedPorts = CopyOnWriteArrayList<Int>()
            val probe = AndroidLanHostProbe(
                pingSessionEngine = FakePingSessionEngine(pingResult(success = false)),
                tcpPortChecker = FakeTcpPortChecker(
                    checkedPorts = checkedPorts,
                    successful = { port -> port == 9_100 },
                ),
            )

            val result = probe.probe("192.168.1.20", LanScanProbeConfig())

            assertEquals(listOf(80, 443, 22, 445, 53, 9_100), checkedPorts)
            assertEquals(9_100, result.evidence.single().successfulPort)
        }

    @Test
    fun `tcp fallback stops after the first positive response`() = kotlinx.coroutines.test.runTest {
        val checkedPorts = CopyOnWriteArrayList<Int>()
        val probe = AndroidLanHostProbe(
            pingSessionEngine = FakePingSessionEngine(pingResult(success = false)),
            tcpPortChecker = FakeTcpPortChecker(
                checkedPorts = checkedPorts,
                successful = { port -> port == 22 },
            ),
        )

        probe.probe("192.168.1.11", LanScanProbeConfig())

        assertEquals(listOf(80, 443, 22), checkedPorts)
    }

    @Test
    fun `connection refused is positive host evidence but not an open port`() =
        kotlinx.coroutines.test.runTest {
            val probe = AndroidLanHostProbe(
                pingSessionEngine = FakePingSessionEngine(pingResult(success = false)),
                tcpPortChecker = FakeTcpPortChecker(
                    checkedPorts = CopyOnWriteArrayList(),
                    successful = { false },
                    refusedPort = 80,
                ),
            )

            val result = probe.probe("192.168.1.12", LanScanProbeConfig())

            assertTrue(result.hasPositiveEvidence)
            assertEquals(80, result.evidence.single().successfulPort)
            assertTrue(result.evidence.single().detail.orEmpty().contains("refused"))
        }

    @Test
    fun `all fallback ports failing produces no discovered evidence`() =
        kotlinx.coroutines.test.runTest {
            val probe = AndroidLanHostProbe(
                pingSessionEngine = FakePingSessionEngine(pingResult(success = false)),
                tcpPortChecker = FakeTcpPortChecker(
                    checkedPorts = CopyOnWriteArrayList(),
                    successful = { false },
                ),
            )

            val result = probe.probe("192.168.1.13", LanScanProbeConfig())

            assertTrue(result.evidence.isEmpty())
        }

    private class FakePingSessionEngine(
        private val result: PingSessionResult,
    ) : PingSessionEngine {
        override suspend fun run(
            request: com.networktoolbox.core.network.ping.PingRequest,
            onProgress: (com.networktoolbox.core.network.ping.PingSessionProgress) -> Unit,
        ): PingSessionResult = result
    }

    private class FakeTcpPortChecker(
        private val checkedPorts: MutableList<Int>,
        private val successful: (Int) -> Boolean = { false },
        private val refusedPort: Int? = null,
    ) : TcpPortChecker {
        override suspend fun check(host: String, port: Int, timeoutMs: Int): TcpProbeResult {
            checkedPorts += port
            return when {
                port == refusedPort -> TcpProbeResult(host, port, false, null, "Connection refused")
                successful(port) -> TcpProbeResult(host, port, true, 8, null)
                else -> TcpProbeResult(host, port, false, null, "Timeout")
            }
        }
    }

    private fun pingResult(success: Boolean): PingSessionResult = PingSessionResult(
        target = "192.168.1.10",
        address = "192.168.1.10",
        protocol = PingProtocol.IPV4,
        mode = PingMode.SINGLE,
        startTime = 0,
        endTime = 12,
        sentPackets = 1,
        receivedPackets = if (success) 1 else 0,
        lostPackets = if (success) 0 else 1,
        packetLoss = if (success) 0.0 else 1.0,
        minLatencyMs = if (success) 12 else null,
        avgLatencyMs = if (success) 12.0 else null,
        maxLatencyMs = if (success) 12 else null,
        jitterMs = null,
        qualityLevel = PingQualityLevel.UNKNOWN,
        summary = "",
        method = if (success) PingMethod.SYSTEM_REACHABILITY else PingMethod.UNAVAILABLE,
        errorMessage = null,
    )
}
