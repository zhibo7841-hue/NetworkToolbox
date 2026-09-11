package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LanNetworkScopeTest {
    @Test
    fun `scope ignores host address changes inside the same prefix`() {
        val first = context(address = "10.0.1.20")
        val second = context(address = "10.0.1.206")

        assertEquals(LanNetworkScope.from(first), LanNetworkScope.from(second))
    }

    @Test
    fun `scope changes with network prefix`() {
        assertNotEquals(
            LanNetworkScope.from(context(address = "10.0.1.20")),
            LanNetworkScope.from(context(address = "10.0.2.20", gateway = "10.0.2.1")),
        )
    }

    @Test
    fun `network fingerprint changes for a different wifi network in the same subnet`() {
        val first = context(address = "10.0.1.20", wifiName = "HomeLab")
        val second = context(address = "10.0.1.30", wifiName = "Guest")

        assertNotEquals(
            LanNetworkFingerprint.from(first),
            LanNetworkFingerprint.from(second),
        )
    }

    @Test
    fun `network fingerprint does not use ssid as the sole network identity`() {
        val first = context(address = "10.0.1.20", wifiName = "HomeLab")
        val second = context(
            address = "10.0.1.30",
            gateway = "10.0.1.254",
            wifiName = "HomeLab",
        )

        assertNotEquals(
            LanNetworkFingerprint.from(first),
            LanNetworkFingerprint.from(second),
        )
    }

    @Test
    fun `scope is opaque and does not expose wifi name`() {
        val scope = LanNetworkScope.from(context(address = "10.0.1.20", wifiName = "PrivateHome"))

        assertNotNull(scope)
        assertFalse(scope!!.contains("PrivateHome"))
        assertFalse(scope.contains("10.0.1.0"))
    }

    @Test
    fun `cellular and unavailable contexts have no lan scope`() {
        assertNull(LanNetworkScope.from(context(address = "10.0.1.20").copy(connectionType = ConnectionType.CELLULAR)))
        assertNull(LanNetworkScope.from(NetworkContext.noActiveNetwork()))
    }

    @Test
    fun `unknown ssid is not a required identity input`() {
        assertNotNull(
            LanNetworkScope.from(
                context(address = "10.0.1.20", wifiName = "<unknown ssid>"),
            ),
        )
    }

    private fun context(
        address: String,
        gateway: String = "10.0.1.1",
        wifiName: String? = null,
    ) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = gateway,
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = wifiName,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )
}
