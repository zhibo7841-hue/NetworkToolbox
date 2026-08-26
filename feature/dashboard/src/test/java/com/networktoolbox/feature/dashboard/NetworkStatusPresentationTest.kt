package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.presentation.Ipv6DisplayStatus
import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusPresentationTest {
    @Test
    fun emptyIpv6List_isNotConfigured() {
        assertEquals(
            Ipv6DisplayStatus.NOT_CONFIGURED,
            NetworkStatusPresentation.ipv6Status(emptyList()),
        )
    }

    @Test
    fun linkLocalOnlyIpv6_isClassifiedAsLinkLocalOnly() {
        assertEquals(
            Ipv6DisplayStatus.LINK_LOCAL_ONLY,
            NetworkStatusPresentation.ipv6Status(listOf("fe80::1")),
        )
    }

    @Test
    fun linkLocalAndNonLinkLocalIpv6_isClassifiedAsConfigured() {
        assertEquals(
            Ipv6DisplayStatus.CONFIGURED,
            NetworkStatusPresentation.ipv6Status(listOf("fe80::1", "2408::1")),
        )
    }

    @Test
    fun dnsSummary_usesCountInsteadOfAddress() {
        assertEquals("未配置", NetworkStatusPresentation.dnsSummary(0))
        assertEquals("1 个服务器", NetworkStatusPresentation.dnsSummary(1))
        assertEquals("4 个服务器", NetworkStatusPresentation.dnsSummary(4))
    }

    @Test
    fun cellular_doesNotShowGatewayOrWifiSignal() {
        val context = context(connectionType = ConnectionType.CELLULAR)

        assertFalse(NetworkStatusPresentation.shouldShowGateway(context))
        assertFalse(NetworkStatusPresentation.shouldShowWifiSignal(context))
    }

    @Test
    fun wifi_showsGatewayAndWifiSignal() {
        val context = context(connectionType = ConnectionType.WIFI)

        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
        assertTrue(NetworkStatusPresentation.shouldShowWifiSignal(context))
    }

    @Test
    fun vpnStateDoesNotDiscardUnderlyingNetworkData() {
        val context = context(connectionType = ConnectionType.WIFI, vpnActive = true)

        assertEquals(ConnectionType.WIFI, context.connectionType)
        assertTrue(context.vpnActive == true)
        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
    }

    private fun context(
        connectionType: ConnectionType,
        vpnActive: Boolean? = false,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = "192.0.2.10",
        ipv6Address = "fe80::10",
        gateway = "192.0.2.1",
        dnsServers = listOf("192.0.2.53"),
        vpnActive = vpnActive,
        wifiName = null,
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        validated = true,
        ipv6Addresses = listOf("fe80::10"),
    )
}
