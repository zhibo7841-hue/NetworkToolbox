package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCenterPresentationTest {
    @Test
    fun `wifi summary keeps current network facts compact`() {
        val context = wifiContext(
            address = "10.0.1.206",
            gateway = "10.0.1.1",
            wifiName = "HomeLab",
            signal = 4,
        )

        val summary = DeviceCenterPresentation.networkSummary(context, readyRange(context))

        assertEquals("Wi-Fi", summary.networkLabel)
        assertEquals("HomeLab", summary.networkName)
        assertEquals("10.0.1.0/24", summary.subnet)
        assertEquals("10.0.1.206", summary.localAddress)
        assertEquals("10.0.1.1", summary.gateway)
        assertEquals(4, summary.wifiSignalLevel)
    }

    @Test
    fun `cellular summary does not expose internal gateway or wifi signal`() {
        val context = wifiContext(
            address = "100.64.0.2",
            gateway = "100.64.0.1",
            signal = 4,
        ).copy(connectionType = ConnectionType.CELLULAR)

        val summary = DeviceCenterPresentation.networkSummary(context)

        assertEquals("移动网络", summary.networkLabel)
        assertEquals("100.64.0.2", summary.localAddress)
        assertNull(summary.gateway)
        assertNull(summary.wifiSignalLevel)
    }

    @Test
    fun `unknown network and missing wifi name stay neutral`() {
        val noNetwork = DeviceCenterPresentation.networkSummary(NetworkContext.noActiveNetwork())
        val unknownSsid = DeviceCenterPresentation.networkSummary(
            wifiContext(address = null, wifiName = "<unknown ssid>", signal = null),
        )

        assertEquals("未知网络", noNetwork.networkLabel)
        assertNull(noNetwork.localAddress)
        assertNull(noNetwork.gateway)
        assertEquals("Wi-Fi", unknownSsid.networkLabel)
        assertNull(unknownSsid.networkName)
    }

    @Test
    fun `device center uses aggregated names and neutral unknown fallback`() {
        val upnpNamed = device("10.0.1.20").copy(
            upnpObservations = listOf(
                LanUpnpObservation(
                    friendlyName = "Living Room Hub",
                    manufacturer = "Example",
                    modelName = "Hub 2",
                    observedAt = 1L,
                ),
            ),
        )
        val mdnsNamed = device("10.0.1.21").copy(
            mdnsObservations = listOf(
                LanMdnsObservation(
                    serviceName = "Office Printer",
                    serviceType = "_ipp._tcp",
                    observedAt = 1L,
                ),
            ),
        )
        val unknown = device("10.0.1.22")

        assertEquals("Living Room Hub", DeviceCenterPresentation.deviceDisplayName(upnpNamed))
        assertEquals("Example · Hub 2", DeviceCenterPresentation.deviceIdentitySummary(upnpNamed))
        assertEquals("Office Printer", DeviceCenterPresentation.deviceDisplayName(mdnsNamed))
        assertEquals("未知设备", DeviceCenterPresentation.deviceDisplayName(unknown))
        assertEquals("可达性检测 · 16 ms", DeviceCenterPresentation.deviceEvidence(unknown))
    }

    @Test
    fun `only gateway and local roles get badges`() {
        val gateway = device("10.0.1.1").copy(isGateway = true)
        val local = device("10.0.1.206").copy(isLocalDevice = true)
        val ordinary = device("10.0.1.30")

        assertEquals("网关", DeviceCenterPresentation.deviceRole(gateway))
        assertEquals("本机", DeviceCenterPresentation.deviceRole(local))
        assertEquals("", DeviceCenterPresentation.deviceRole(ordinary))
        assertEquals("网关信息", DeviceCenterPresentation.deviceEvidence(gateway))
        assertEquals("当前设备", DeviceCenterPresentation.deviceEvidence(local))
    }

    private fun readyRange(context: NetworkContext) =
        (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range

    private fun device(ipAddress: String) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = false,
        isGateway = false,
        latencyMs = 16L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 1L,
    )

    private fun wifiContext(
        address: String?,
        gateway: String? = null,
        wifiName: String? = null,
        signal: Int? = null,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = wifiName,
        wifiSignalLevel = signal,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )
}
