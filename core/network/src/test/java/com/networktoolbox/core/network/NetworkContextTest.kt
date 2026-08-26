package com.networktoolbox.core.network

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkContextTest {
    @Test
    fun unknownContextUsesSafeDefaults() {
        val context = NetworkContext.unknown()

        assertEquals(ConnectionType.UNKNOWN, context.connectionType)
        assertNull(context.ipv4Address)
        assertNull(context.ipv6Address)
        assertNull(context.gateway)
        assertEquals(emptyList<String>(), context.dnsServers)
        assertNull(context.vpnActive)
        assertNull(context.wifiName)
        assertNull(context.wifiSignalLevel)
        assertNull(context.activeNetworkAvailable)
        assertNull(context.validated)
    }

    @Test
    fun noActiveNetworkIsExplicitlyRepresented() {
        val context = NetworkContext.noActiveNetwork()

        assertEquals(false, context.activeNetworkAvailable)
        assertEquals(false, context.validated)
    }
}
