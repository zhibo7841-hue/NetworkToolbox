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
    fun ipv4Prefix_isConvertedToNetmask() {
        assertEquals("0.0.0.0", NetworkStatusPresentation.ipv4PrefixToNetmask(0))
        assertEquals("255.0.0.0", NetworkStatusPresentation.ipv4PrefixToNetmask(8))
        assertEquals("255.255.255.0", NetworkStatusPresentation.ipv4PrefixToNetmask(24))
        assertEquals("255.255.255.128", NetworkStatusPresentation.ipv4PrefixToNetmask(25))
        assertEquals("255.255.255.255", NetworkStatusPresentation.ipv4PrefixToNetmask(32))
        assertEquals(null, NetworkStatusPresentation.ipv4PrefixToNetmask(33))
    }

    @Test
    fun preferredDns_prefersIpv4OverIpv6() {
        assertEquals(
            "192.0.2.53",
            NetworkStatusPresentation.preferredDnsForSummary(
                listOf("2001:db8::53", "192.0.2.53", "192.0.2.54"),
            ),
        )
    }

    @Test
    fun preferredDns_usesIpv6WhenIpv4IsUnavailable() {
        assertEquals(
            "2001:db8::53",
            NetworkStatusPresentation.preferredDnsForSummary(listOf("2001:db8::53")),
        )
        assertEquals(null, NetworkStatusPresentation.preferredDnsForSummary(emptyList()))
    }

    @Test
    fun primaryAddress_prefersIpv4AndHidesIpv6FromDefaultSummary() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(
                ipv4Address = "192.0.2.10",
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
            ),
        )

        assertEquals("IPv4 地址", summary.label)
        assertEquals("192.0.2.10", summary.value)
    }

    @Test
    fun primaryAddress_usesGlobalIpv6WhenIpv4IsMissing() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(
                ipv4Address = null,
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
            ),
        )

        assertEquals("IPv6 地址", summary.label)
        assertEquals("2001:db8::10", summary.value)
    }

    @Test
    fun primaryAddress_describesLinkLocalOnlyWhenNoGlobalIpv6Exists() {
        val summary = NetworkStatusPresentation.primaryAddressForSummary(
            context(ipv4Address = null, ipv6Addresses = listOf("fe80::10")),
        )

        assertEquals("IPv6", summary.label)
        assertEquals("仅链路本地", summary.value)
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
        connectionType: ConnectionType = ConnectionType.WIFI,
        vpnActive: Boolean? = false,
        ipv4Address: String? = "192.0.2.10",
        ipv6Addresses: List<String> = listOf("fe80::10"),
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = ipv4Address,
        ipv6Address = "fe80::10",
        gateway = "192.0.2.1",
        dnsServers = listOf("192.0.2.53"),
        vpnActive = vpnActive,
        wifiName = null,
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        validated = true,
        ipv6Addresses = ipv6Addresses,
    )
}
