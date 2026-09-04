package com.networktoolbox.core.network

import com.networktoolbox.core.network.data.NetworkContextMapper
import com.networktoolbox.core.network.data.NetworkContextSnapshot
import com.networktoolbox.core.network.model.ConnectionType
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkContextMapperTest {
    @Test
    fun snapshotIsConvertedToNetworkContext() {
        val result = NetworkContextMapper.map(
            NetworkContextSnapshot(
                connectionType = ConnectionType.WIFI,
                ipv4Address = "192.168.1.20",
                ipv6Address = "2001:db8::20",
                ipv6Addresses = listOf("fe80::20", "2001:db8::20"),
                ipv4PrefixLength = 24,
                gateway = "192.168.1.1",
                dnsServers = listOf("223.5.5.5"),
                vpnActive = true,
                wifiName = "HomeWiFi",
                wifiSignalLevel = 4,
                activeNetworkAvailable = true,
                validated = true,
                interfaceName = "wlan0",
                privateDnsActive = true,
                privateDnsServerName = "dns.example",
                captivePortal = false,
                partialConnectivity = true,
            ),
        )

        assertEquals(ConnectionType.WIFI, result.connectionType)
        assertEquals("192.168.1.20", result.ipv4Address)
        assertEquals("2001:db8::20", result.ipv6Address)
        assertEquals(listOf("fe80::20", "2001:db8::20"), result.ipv6Addresses)
        assertEquals(24, result.ipv4PrefixLength)
        assertEquals("192.168.1.1", result.gateway)
        assertEquals(listOf("223.5.5.5"), result.dnsServers)
        assertEquals(true, result.vpnActive)
        assertEquals("HomeWiFi", result.wifiName)
        assertEquals(4, result.wifiSignalLevel)
        assertEquals(true, result.activeNetworkAvailable)
        assertEquals(true, result.validated)
        assertEquals("wlan0", result.interfaceName)
        assertEquals(true, result.privateDnsActive)
        assertEquals("dns.example", result.privateDnsServerName)
        assertEquals(false, result.captivePortal)
        assertEquals(true, result.partialConnectivity)
    }
}
