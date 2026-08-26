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
                gateway = "192.168.1.1",
                dnsServers = listOf("223.5.5.5"),
                vpnActive = true,
                wifiName = "HomeWiFi",
                wifiSignalLevel = 4,
                activeNetworkAvailable = true,
                validated = true,
            ),
        )

        assertEquals(ConnectionType.WIFI, result.connectionType)
        assertEquals("192.168.1.20", result.ipv4Address)
        assertEquals("2001:db8::20", result.ipv6Address)
        assertEquals("192.168.1.1", result.gateway)
        assertEquals(listOf("223.5.5.5"), result.dnsServers)
        assertEquals(true, result.vpnActive)
        assertEquals("HomeWiFi", result.wifiName)
        assertEquals(4, result.wifiSignalLevel)
        assertEquals(true, result.activeNetworkAvailable)
        assertEquals(true, result.validated)
    }
}
