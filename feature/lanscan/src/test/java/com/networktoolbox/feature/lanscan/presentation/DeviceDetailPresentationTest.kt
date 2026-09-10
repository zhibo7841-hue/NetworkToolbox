package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDetailPresentationTest {
    @Test
    fun `unknown detail stays neutral and keeps ip`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("10.0.1.22"),
            favorite = null,
            context = context(),
        )

        assertEquals("未知设备", detail.displayName)
        assertEquals("10.0.1.22", detail.ipAddress)
        assertTrue(detail.observedThisScan)
        assertFalse(detail.isFavorite)
        assertNull(detail.macAddress)
    }

    @Test
    fun `detail exposes observed identity fields and roles`() {
        val detail = DeviceCenterPresentation.detail(
            device = device("10.0.1.1", gateway = true).copy(
                macAddress = "aa-bb-cc-dd-ee-ff",
                mdnsObservations = listOf(
                    LanMdnsObservation(
                        serviceName = "Printer",
                        serviceType = "_ipp._tcp",
                        hostname = "printer.local",
                        observedAt = 1L,
                    ),
                ),
                upnpObservations = listOf(
                    LanUpnpObservation(
                        friendlyName = "Home Router",
                        manufacturer = "Example",
                        modelName = "Router 1",
                        observedAt = 1L,
                    ),
                ),
            ),
            favorite = null,
            context = context(),
        )

        assertEquals("Home Router", detail.displayName)
        assertEquals("AA:BB:CC:DD:EE:FF", detail.macAddress)
        assertEquals("Example", detail.vendor)
        assertEquals("Router 1", detail.model)
        assertEquals("网关", detail.role)
        assertTrue(detail.mdnsNames.contains("Printer"))
        assertTrue(detail.mdnsNames.contains("printer.local"))
        assertEquals(listOf("Home Router"), detail.upnpNames)
        assertEquals("当前局域网", detail.networkScope)
    }

    @Test
    fun `saved detail remains available when not observed`() {
        val favorite = FavoriteDevice(
            id = 1L,
            identityType = FavoriteIdentityType.NETWORK_IP,
            identityValue = "10.0.1.50",
            networkScope = "scope",
            lastKnownIpv4 = "10.0.1.50",
            lastKnownDisplayName = "Home Server",
            lastKnownHostname = "server.local",
            lastKnownMdnsName = "Server",
            lastKnownUpnpName = null,
            macAddress = null,
            vendor = "Example",
            model = "Server 1",
            createdAt = 1L,
            lastSeenAt = 123L,
            isGateway = false,
            isLocalDevice = false,
        )

        val detail = DeviceCenterPresentation.detail(favorite, context())

        assertEquals("Home Server", detail.displayName)
        assertEquals("10.0.1.50", detail.ipAddress)
        assertEquals("当前局域网", detail.networkScope)
        assertFalse(detail.observedThisScan)
        assertTrue(detail.isFavorite)
        assertEquals("server.local", detail.hostname)
        assertEquals(listOf("Server"), detail.mdnsNames)
        assertEquals(123L, detail.lastSeenAt)
    }

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
    )

    private fun device(ip: String, gateway: Boolean = false) = LanDevice(
        ipAddress = ip,
        isLocalDevice = false,
        isGateway = gateway,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 100L,
    )
}
