package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCenterFavoritesTest {
    @Test
    fun `device center orders favorite observed roles other observed and unseen favorite`() {
        val context = context()
        val scope = LanNetworkScope.from(context)!!
        val favorite = favorite(ip = "10.0.1.40", scope = scope)
        val devices = listOf(
            device("10.0.1.10"),
            device("10.0.1.2", local = true),
            device("10.0.1.1", gateway = true),
            device("10.0.1.40"),
        )
        val unseen = favorite(ip = "10.0.1.50", scope = scope, id = 2L)

        val items = DeviceCenterPresentation.deviceList(
            devices = devices,
            favorites = listOf(favorite, unseen),
            context = context,
        )

        assertEquals(
            listOf("10.0.1.40", "10.0.1.1", "10.0.1.2", "10.0.1.10", "10.0.1.50"),
            items.map { it.card.ipAddress },
        )
        assertTrue(items[0].isFavorite)
        assertTrue(items[0].observedThisScan)
        assertFalse(items.last().observedThisScan)
        assertEquals("本次未发现", items.last().card.evidence)
    }

    @Test
    fun `favorite from another network is not mixed into current device center`() {
        val context = context()
        val items = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.10")),
            favorites = listOf(favorite(ip = "10.0.1.10", scope = "other-scope")),
            context = context,
        )

        assertEquals(1, items.size)
        assertFalse(items.single().isFavorite)
    }

    @Test
    fun `unseen favorite is not described as offline`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = listOf(favorite(ip = "10.0.1.50", scope = LanNetworkScope.from(context)!!)),
            context = context,
        ).single()

        assertFalse(item.observedThisScan)
        assertEquals("本次未发现", item.card.evidence)
        assertFalse(item.card.evidence.orEmpty().contains("离线"))
    }

    @Test
    fun `custom named nonfavorite profile remains visible when not observed`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = emptyList(),
            favorites = listOf(
                favorite(
                    ip = "10.0.1.51",
                    scope = LanNetworkScope.from(context)!!,
                ).copy(isFavorite = false, customName = "HomeLab NAS"),
            ),
            context = context,
        ).single()

        assertEquals("HomeLab NAS", item.card.displayName)
        assertFalse(item.isFavorite)
        assertFalse(item.observedThisScan)
        assertEquals("本次未发现", item.card.evidence)
    }

    @Test
    fun `custom name overrides detected identity without changing favorite state`() {
        val context = context()
        val item = DeviceCenterPresentation.deviceList(
            devices = listOf(device("10.0.1.10")),
            favorites = listOf(
                favorite(
                    ip = "10.0.1.10",
                    scope = LanNetworkScope.from(context)!!,
                ).copy(customName = "书房设备", isFavorite = false),
            ),
            context = context,
        ).single()

        assertEquals("书房设备", item.card.displayName)
        assertFalse(item.isFavorite)
    }

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )

    private fun device(ip: String, local: Boolean = false, gateway: Boolean = false) = LanDevice(
        ipAddress = ip,
        isLocalDevice = local,
        isGateway = gateway,
        latencyMs = 16L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 100L,
    )

    private fun favorite(ip: String, scope: String, id: Long = 1L) = FavoriteDevice(
        id = id,
        identityType = FavoriteIdentityType.NETWORK_IP,
        identityValue = ip,
        networkScope = scope,
        lastKnownIpv4 = ip,
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = null,
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
    )
}
