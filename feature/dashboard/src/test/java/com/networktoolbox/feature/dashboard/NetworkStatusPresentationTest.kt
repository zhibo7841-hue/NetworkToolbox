package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.designsystem.StatusVisualState
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.dashboard.presentation.Ipv6DisplayStatus
import com.networktoolbox.feature.dashboard.presentation.NetworkStatusPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("移动网络", NetworkStatusPresentation.networkIdentity(context))
        assertEquals(
            "不适用",
            NetworkStatusPresentation.summaryMetrics(context)[2].value,
        )
    }

    @Test
    fun wifi_showsGatewayAndWifiSignal() {
        val context = context(connectionType = ConnectionType.WIFI)

        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
        assertTrue(NetworkStatusPresentation.shouldShowWifiSignal(context))
    }

    @Test
    fun wifiSignalStrength_mapsKnownLevelsAndUnknownValues() {
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.STRONG,
            NetworkStatusPresentation.wifiSignalStrength(4),
        )
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.MEDIUM,
            NetworkStatusPresentation.wifiSignalStrength(2),
        )
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.WEAK,
            NetworkStatusPresentation.wifiSignalStrength(1),
        )
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.WifiSignalStrength.UNKNOWN,
            NetworkStatusPresentation.wifiSignalStrength(null),
        )
        assertEquals("Wi-Fi 信号强", NetworkStatusPresentation.wifiSignalContentDescription(4))
        assertEquals("Wi-Fi 信号未知", NetworkStatusPresentation.wifiSignalContentDescription(null))
    }

    @Test
    fun heroIconKind_followsNetworkTypeAndConnectivity() {
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.WIFI_STRONG,
            NetworkStatusPresentation.networkHeroIconKind(context(wifiSignalLevel = 4)),
        )
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.CELLULAR,
            NetworkStatusPresentation.networkHeroIconKind(
                context(connectionType = ConnectionType.CELLULAR),
            ),
        )
        assertEquals(
            com.networktoolbox.feature.dashboard.presentation.NetworkHeroIconKind.DISCONNECTED,
            NetworkStatusPresentation.networkHeroIconKind(NetworkContext.noActiveNetwork()),
        )
    }

    @Test
    fun vpnStateDoesNotDiscardUnderlyingNetworkData() {
        val context = context(connectionType = ConnectionType.WIFI, vpnActive = true)

        assertEquals(ConnectionType.WIFI, context.connectionType)
        assertTrue(context.vpnActive == true)
        assertTrue(NetworkStatusPresentation.shouldShowGateway(context))
    }

    @Test
    fun noActiveNetwork_mapsToExplicitDisconnectedState() {
        val context = NetworkContext.noActiveNetwork()

        assertEquals("当前没有活动网络", NetworkStatusPresentation.networkIdentity(context))
        assertEquals("未连接", NetworkStatusPresentation.connectionStatusLabel(context))
        assertEquals(
            StatusVisualState.ERROR,
            NetworkStatusPresentation.connectionStatusVisualState(context),
        )
        assertEquals("未配置", NetworkStatusPresentation.dnsSummary(context.dnsServers))
    }

    @Test
    fun unknownNetwork_mapsToUnknownStatusWithoutInventingConnectivity() {
        val context = NetworkContext.unknown()

        assertEquals("状态未知", NetworkStatusPresentation.connectionStatusLabel(context))
        assertEquals(
            StatusVisualState.UNKNOWN,
            NetworkStatusPresentation.connectionStatusVisualState(context),
        )
    }

    @Test
    fun dnsSummary_countsDistinctNonBlankServers() {
        assertEquals(
            "2 个服务器",
            NetworkStatusPresentation.dnsSummary(
                listOf("192.0.2.53", "192.0.2.53", "2001:db8::53", " "),
            ),
        )
        assertEquals("1 个服务器", NetworkStatusPresentation.dnsSummary(listOf("192.0.2.53")))
        assertEquals("未配置", NetworkStatusPresentation.dnsSummary(emptyList()))
    }

    @Test
    fun dnsSummaryValue_prefersIpv4AndShowsOnlyOneAddress() {
        assertEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(listOf("192.0.2.53")),
        )
        assertEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("2001:db8::53", "192.0.2.53"),
            ),
        )
        assertEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("192.0.2.53", "2001:db8::53", "192.0.2.53"),
            ),
        )
        assertEquals("未配置", NetworkStatusPresentation.dnsSummaryValue(emptyList()))
    }

    @Test
    fun dnsSummaryValue_withMultipleIpv4Servers_showsOnlyTheFirst() {
        assertEquals(
            "192.0.2.53",
            NetworkStatusPresentation.dnsSummaryValue(
                listOf("192.0.2.53", "192.0.2.54", "192.0.2.55", "192.0.2.56"),
            ),
        )
    }

    @Test
    fun dnsSummaryValue_fallsBackToIpv6WhenNoIpv4Exists() {
        assertEquals(
            "2001:db8::53",
            NetworkStatusPresentation.dnsSummaryValue(listOf("2001:db8::53")),
        )
    }

    @Test
    fun summaryMetrics_keepExactlyFourCoreValuesAndHideIpv6AndSignal() {
        val metrics = NetworkStatusPresentation.summaryMetrics(
            context(
                ipv6Addresses = listOf("fe80::10", "2001:db8::10"),
                dnsServers = listOf("192.0.2.53", "2001:db8::53"),
                ipv4PrefixLength = 24,
            ),
        )

        assertEquals(
            listOf("IPv4 地址", "子网掩码", "默认网关", "DNS"),
            metrics.map { it.label },
        )
        assertEquals("255.255.255.0", metrics[1].value)
        assertFalse(metrics.any { it.label == "IPv6" })
        assertFalse(metrics.any { it.label == "信号" })
    }

    @Test
    fun heroMetricLayout_usesReadableTwoColumnFallback() {
        assertTrue(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(360, 1f))
        assertTrue(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(600, 1.2f))
        assertFalse(NetworkStatusPresentation.shouldUseTwoColumnHeroMetrics(600, 1f))
    }

    @Test
    fun unknownWifiName_isNotPresentedAsNetworkIdentity() {
        assertNull(NetworkStatusPresentation.displayableWifiName("<unknown ssid>"))
        assertNull(NetworkStatusPresentation.displayableWifiName("  "))
        assertEquals("Lab Wi-Fi", NetworkStatusPresentation.displayableWifiName(" Lab Wi-Fi "))
    }

    @Test
    fun longWifiName_remainsRealDataForEllipsizedHero() {
        val longName = "HomeLab-WiFi-5G-非常长的网络名称"

        assertEquals(longName, NetworkStatusPresentation.displayableWifiName(longName))
        assertEquals(
            longName,
            NetworkStatusPresentation.networkIdentity(
                context(wifiName = longName),
            ),
        )
    }

    private fun context(
        connectionType: ConnectionType = ConnectionType.WIFI,
        vpnActive: Boolean? = false,
        ipv4Address: String? = "192.0.2.10",
        ipv6Addresses: List<String> = listOf("fe80::10"),
        wifiSignalLevel: Int? = 3,
        dnsServers: List<String> = listOf("192.0.2.53"),
        ipv4PrefixLength: Int? = null,
        wifiName: String? = null,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = ipv4Address,
        ipv6Address = "fe80::10",
        gateway = "192.0.2.1",
        dnsServers = dnsServers,
        vpnActive = vpnActive,
        wifiName = wifiName,
        wifiSignalLevel = wifiSignalLevel,
        activeNetworkAvailable = true,
        validated = true,
        ipv6Addresses = ipv6Addresses,
        ipv4PrefixLength = ipv4PrefixLength,
    )
}
