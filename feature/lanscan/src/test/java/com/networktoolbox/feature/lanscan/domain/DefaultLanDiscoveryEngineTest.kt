package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRequest
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLanDiscoveryEngineTest {
    @Test
    fun `successful reachability adds online device and emits progressive updates`() = runTest {
        val updates = mutableListOf<com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate>()
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                LanHostProbeResult(
                    ipAddress = ipAddress,
                    evidence = listOf(
                        LanDeviceEvidence(
                            method = LanDiscoveryMethod.REACHABILITY,
                            latencyMs = 10,
                        ),
                    ),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 30, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context("192.168.1.2", 30, gateway = "192.168.1.1") },
            onUpdate = updates::add,
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertEquals(2, session.scannedHosts)
        assertEquals(2, session.totalHosts)
        assertEquals(listOf("192.168.1.1", "192.168.1.2"), session.discoveredDevices.map { it.ipAddress })
        assertEquals(3, updates.count { it.status == LanScanStatus.SCANNING })
        assertTrue(updates.any { it.status == LanScanStatus.COMPLETED })
    }

    @Test
    fun `local and gateway are deterministic devices even when probes are skipped`() = runTest {
        val probedHosts = mutableListOf<String>()
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                probedHosts += ipAddress
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.2", 29, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context("192.168.1.2", 29, gateway = "192.168.1.1") },
        )

        val gateway = session.discoveredDevices.first { it.isGateway }
        val local = session.discoveredDevices.first { it.isLocalDevice }
        assertEquals("192.168.1.1", gateway.ipAddress)
        assertEquals("192.168.1.2", local.ipAddress)
        assertEquals(listOf("192.168.1.3", "192.168.1.4", "192.168.1.5", "192.168.1.6"), probedHosts)
    }

    @Test
    fun `all fallback failures omit ordinary hosts without claiming offline`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ -> LanHostProbeResult(ipAddress) },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.8", 29, gateway = null),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
                currentNetworkContext = { context("192.168.1.8", 29, gateway = null) },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertTrue(session.discoveredDevices.none { it.ipAddress == "192.168.1.1" })
        assertTrue(session.discoveredDevices.isEmpty())
    }

    @Test
    fun `devices use numeric ipv4 ordering after gateway and local markers`() = runTest {
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.TCP, successfulPort = 443)),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.100", 24, gateway = "192.168.1.1"),
                probeConfig = LanScanProbeConfig(maxConcurrency = 16),
            ),
            currentNetworkContext = { context("192.168.1.100", 24, gateway = "192.168.1.1") },
        )

        val addresses = session.discoveredDevices.map { it.ipAddress }
        assertEquals("192.168.1.1", addresses.first())
        assertEquals("192.168.1.100", addresses[1])
        assertTrue(addresses.indexOf("192.168.1.2") < addresses.indexOf("192.168.1.10"))
        assertTrue(addresses.indexOf("192.168.1.10") < addresses.indexOf("192.168.1.11"))
    }

    @Test
    fun `bounded worker pool never exceeds configured concurrency`() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                val current = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, current) }
                delay(2)
                active.decrementAndGet()
                LanHostProbeResult(ipAddress)
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("10.0.0.200", 24, gateway = null),
                probeConfig = LanScanProbeConfig(maxConcurrency = 4),
            ),
            currentNetworkContext = { context("10.0.0.200", 24, gateway = null) },
        )

        assertEquals(LanScanStatus.COMPLETED, session.status)
        assertTrue(maxActive.get() <= 4)
    }

    @Test
    fun `cancellation returns cancelled session and does not start new probes`() = runTest {
        val calls = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                calls.incrementAndGet()
                throw CancellationException("test cancellation")
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = context("192.168.1.100", 24, gateway = null),
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { context("192.168.1.100", 24, gateway = null) },
        )

        assertEquals(LanScanStatus.CANCELLED, session.status)
        assertTrue(calls.get() <= 1)
        assertTrue(session.scannedHosts == 0)
    }

    @Test
    fun `network change stops scan before mixing a second network`() = runTest {
        val changed = AtomicReference(false)
        val initial = context("192.168.1.8", 29, gateway = "192.168.1.1")
        val next = context("192.168.2.8", 29, gateway = "192.168.2.1")
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                changed.set(true)
                LanHostProbeResult(
                    ipAddress,
                    listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
                )
            },
        )

        val session = engine.scan(
            request = LanScanRequest(
                networkContext = initial,
                probeConfig = LanScanProbeConfig(maxConcurrency = 1),
            ),
            currentNetworkContext = { if (changed.get()) next else initial },
        )

        assertEquals(LanScanStatus.NETWORK_CHANGED, session.status)
        assertTrue(session.networkChanged)
        assertTrue(session.scannedHosts <= 1)
        assertFalse(session.discoveredDevices.any { it.ipAddress.startsWith("192.168.2.") })
    }

    @Test
    fun `unsupported cellular and vpn sessions do not invoke host probe`() = runTest {
        val calls = AtomicInteger(0)
        val engine = DefaultLanDiscoveryEngine(
            hostProbe = LanHostProbe { ipAddress, _ ->
                calls.incrementAndGet()
                LanHostProbeResult(ipAddress)
            },
        )

        val cellular = engine.scan(
            LanScanRequest(context("10.0.0.5", 24, null).copy(connectionType = ConnectionType.CELLULAR)),
            currentNetworkContext = { context("10.0.0.5", 24, null).copy(connectionType = ConnectionType.CELLULAR) },
        )
        val vpn = engine.scan(
            LanScanRequest(context("10.0.0.5", 24, null).copy(vpnActive = true)),
            currentNetworkContext = { context("10.0.0.5", 24, null).copy(vpnActive = true) },
        )

        assertEquals(LanScanStatus.UNSUPPORTED_NETWORK, cellular.status)
        assertEquals(LanScanStatus.VPN_BLOCKED, vpn.status)
        assertEquals(0, calls.get())
    }

    private fun context(
        address: String,
        prefixLength: Int,
        gateway: String?,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = prefixLength,
    )
}
