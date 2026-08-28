package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanScanRangeCalculatorTest {
    private val calculator = LanScanRangeCalculator()

    @Test
    fun `slash 24 range excludes network and broadcast`() {
        val range = ready("10.0.1.206", 24)

        assertEquals("10.0.1.0", range.networkAddress)
        assertEquals("10.0.1.255", range.broadcastAddress)
        assertEquals("10.0.1.1", range.firstHost)
        assertEquals("10.0.1.254", range.lastHost)
        assertEquals(254, range.hostCount)
        assertEquals(254, range.hostAddresses().size)
        assertEquals(false, range.rangeWasLimited)
    }

    @Test
    fun `slash 25 range is calculated from the host address`() {
        val range = ready("192.168.1.130", 25)

        assertEquals("192.168.1.128", range.networkAddress)
        assertEquals("192.168.1.255", range.broadcastAddress)
        assertEquals("192.168.1.129", range.firstHost)
        assertEquals("192.168.1.254", range.lastHost)
        assertEquals(126, range.hostCount)
    }

    @Test
    fun `slash 26 range is calculated from the host address`() {
        val range = ready("192.168.1.70", 26)

        assertEquals("192.168.1.64", range.networkAddress)
        assertEquals("192.168.1.127", range.broadcastAddress)
        assertEquals("192.168.1.65", range.firstHost)
        assertEquals("192.168.1.126", range.lastHost)
        assertEquals(62, range.hostCount)
    }

    @Test
    fun `large subnet is limited to the current ipv4 slash 24`() {
        val range = ready("10.1.2.3", 8)

        assertEquals("10.1.2.0", range.networkAddress)
        assertEquals("10.1.2.255", range.broadcastAddress)
        assertEquals("10.0.0.0", range.originalNetworkAddress)
        assertEquals("10.255.255.255", range.originalBroadcastAddress)
        assertEquals(16_777_214L, range.originalHostCount)
        assertEquals(254, range.hostCount)
        assertTrue(range.rangeWasLimited)
    }

    @Test
    fun `cellular is rejected before range calculation`() {
        val result = calculator.calculate(
            context("10.0.0.5", 24).copy(connectionType = ConnectionType.CELLULAR),
        )

        assertEquals(
            LanScanRangeResult.Rejected(
                LanScanRejectionReason.UNSUPPORTED_NETWORK,
                "局域网扫描需要连接 Wi-Fi 或以太网。",
            ),
            result,
        )
    }

    @Test
    fun `vpn is blocked even when it reports a local looking address`() {
        val result = calculator.calculate(context("10.0.0.5", 24).copy(vpnActive = true))

        assertTrue(result is LanScanRangeResult.Rejected)
        assertEquals(
            LanScanRejectionReason.VPN_BLOCKED,
            (result as LanScanRangeResult.Rejected).reason,
        )
    }

    @Test
    fun `missing active network is rejected`() {
        val result = calculator.calculate(
            context("10.0.0.5", 24).copy(activeNetworkAvailable = false),
        )

        assertEquals(
            LanScanRejectionReason.NO_ACTIVE_NETWORK,
            (result as LanScanRangeResult.Rejected).reason,
        )
    }

    @Test
    fun `invalid address and prefix are rejected`() {
        val invalidAddress = calculator.calculate(context("10.0.0.999", 24))
        val invalidPrefix = calculator.calculate(context("10.0.0.5", 33))

        assertEquals(LanScanRejectionReason.NO_IPV4_ADDRESS, rejected(invalidAddress).reason)
        assertEquals(LanScanRejectionReason.INVALID_PREFIX, rejected(invalidPrefix).reason)
    }

    private fun ready(address: String, prefixLength: Int) =
        (calculator.calculate(context(address, prefixLength)) as LanScanRangeResult.Ready).range

    private fun rejected(result: LanScanRangeResult) = result as LanScanRangeResult.Rejected

    private fun context(address: String, prefixLength: Int) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = "10.0.0.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = prefixLength,
    )
}
