package com.networktoolbox.feature.lanscan.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanDeviceIdentityAggregatorTest {
    @Test
    fun `upnp friendly name has priority and identity fields keep their sources`() {
        val identity = device(hostName = "router.example.lan").copy(
            mdnsObservations = listOf(
                mdns(serviceName = "Printer", hostname = "printer.local"),
            ),
            upnpObservations = listOf(
                upnp(
                    friendlyName = "Living Room Hub",
                    manufacturer = "Xiaomi",
                    modelName = "Smart Home Hub",
                    modelNumber = "M1",
                    deviceType = "urn:schemas-upnp-org:device:Basic:1",
                ),
            ),
        ).identity

        assertEquals("Living Room Hub", identity.displayName.value)
        assertEquals(LanIdentitySource.UPNP, identity.displayName.source)
        assertEquals("Xiaomi", identity.manufacturer?.value)
        assertEquals(LanIdentitySource.UPNP, identity.manufacturer?.source)
        assertEquals("Smart Home Hub", identity.modelName?.value)
        assertEquals("M1", identity.modelNumber?.value)
        assertEquals("router.example.lan", identity.hostname?.value)
        assertTrue(identity.sources.contains(LanIdentitySource.UPNP))
        assertTrue(identity.sources.contains(LanIdentitySource.MDNS))
        assertTrue(identity.sources.contains(LanIdentitySource.REVERSE_DNS))
    }

    @Test
    fun `mdns name wins over reverse dns and preserves local suffix`() {
        val identity = device(hostName = "server.example.lan").copy(
            mdnsObservations = listOf(mdns(serviceName = "Kitchen Printer", hostname = "printer.local")),
        ).identity

        assertEquals("Kitchen Printer", identity.displayName.value)
        assertEquals(LanIdentitySource.MDNS, identity.displayName.source)
        assertEquals("server.example.lan", identity.hostname?.value)
        assertEquals(LanIdentitySource.REVERSE_DNS, identity.hostname?.source)
    }

    @Test
    fun `generic upnp name does not replace meaningful reverse dns`() {
        val identity = device(hostName = "home-server.lan").copy(
            upnpObservations = listOf(upnp(friendlyName = "UPnP Device")),
        ).identity

        assertEquals("home-server.lan", identity.displayName.value)
        assertEquals(LanIdentitySource.REVERSE_DNS, identity.displayName.source)
    }

    @Test
    fun `all configured generic names are ignored`() {
        listOf("Device", "Unknown", "UPnP Device", "Router", "localhost").forEach { genericName ->
            val identity = device("10.0.1.44").copy(
                hostName = genericName,
                hostNameSource = LanDeviceNameSource.REVERSE_DNS,
                mdnsDisplayNameCandidate = genericName,
                upnpDisplayNameCandidate = genericName,
                upnpObservations = listOf(upnp(friendlyName = genericName)),
                mdnsObservations = listOf(mdns(serviceName = genericName)),
            ).identity

            assertEquals("10.0.1.44", identity.displayName.value)
            assertEquals(LanIdentitySource.IP_FALLBACK, identity.displayName.source)
        }
    }

    @Test
    fun `blank ip and overlong names fall back to ip`() {
        val longName = "x".repeat(129)
        val identity = device("10.0.1.40").copy(
            hostName = "10.0.1.40",
            upnpDisplayNameCandidate = longName,
            mdnsDisplayNameCandidate = "Unknown",
            upnpObservations = listOf(upnp(friendlyName = "")),
            mdnsObservations = listOf(mdns(serviceName = "Router")),
        ).identity

        assertEquals("10.0.1.40", identity.displayName.value)
        assertEquals(LanIdentitySource.IP_FALLBACK, identity.displayName.source)
        assertTrue(identity.hostname == null)
    }

    @Test
    fun `ipv6 literal is not treated as a device name`() {
        val identity = device("10.0.1.41").copy(
            hostName = "2001:db8::41",
            hostNameSource = LanDeviceNameSource.REVERSE_DNS,
        ).identity

        assertEquals("10.0.1.41", identity.displayName.value)
        assertFalse(identity.sources.contains(LanIdentitySource.REVERSE_DNS))
    }

    @Test
    fun `aggregation is independent of observation arrival order`() {
        val first = device("10.0.1.42").copy(
            mdnsObservations = listOf(
                mdns(serviceName = "Zeta", hostname = "zeta.local"),
                mdns(serviceName = "Alpha", hostname = "alpha.local"),
            ),
            upnpObservations = listOf(
                upnp(friendlyName = "Zeta Hub", manufacturer = "Vendor B"),
                upnp(friendlyName = "Alpha Hub", manufacturer = "Vendor A"),
            ),
        ).identity
        val reversed = device("10.0.1.42").copy(
            mdnsObservations = listOf(
                mdns(serviceName = "Alpha", hostname = "alpha.local"),
                mdns(serviceName = "Zeta", hostname = "zeta.local"),
            ),
            upnpObservations = listOf(
                upnp(friendlyName = "Alpha Hub", manufacturer = "Vendor A"),
                upnp(friendlyName = "Zeta Hub", manufacturer = "Vendor B"),
            ),
        ).identity

        assertEquals(first, reversed)
        assertEquals("Alpha Hub", first.displayName.value)
        assertEquals("Vendor A", first.manufacturer?.value)
        assertEquals("Alpha", first.services.first { it.source == LanIdentitySource.MDNS }.serviceName)
    }

    @Test
    fun `services are retained with source labels and duplicate services are removed`() {
        val identity = device("10.0.1.43").copy(
            mdnsObservations = listOf(
                mdns(serviceName = "Printer", serviceType = "_ipp._tcp", port = 631),
                mdns(serviceName = "Printer", serviceType = "_ipp._tcp", port = 631),
            ),
            upnpObservations = listOf(
                upnp(
                    services = listOf(
                        LanUpnpService("urn:schemas-upnp-org:service:SwitchPower:1", "switch"),
                    ),
                ),
            ),
        ).identity

        assertEquals(2, identity.services.size)
        assertTrue(identity.services.any { it.source == LanIdentitySource.MDNS && it.port == 631 })
        assertTrue(identity.services.any { it.source == LanIdentitySource.UPNP })
    }

    @Test
    fun `local and gateway role flags remain on the original device`() {
        val device = device("10.0.1.1", isGateway = true)
        val local = device("10.0.1.206", isLocal = true)

        assertTrue(device.isGateway)
        assertFalse(device.isLocalDevice)
        assertTrue(local.isLocalDevice)
        assertFalse(local.isGateway)
        assertEquals("10.0.1.1", device.identity.displayName.value)
        assertEquals("10.0.1.206", local.identity.displayName.value)
    }

    private fun device(
        ipAddress: String = "10.0.1.20",
        hostName: String? = null,
        isLocal: Boolean = false,
        isGateway: Boolean = false,
    ) = LanDevice(
        ipAddress = ipAddress,
        hostName = hostName,
        isLocalDevice = isLocal,
        isGateway = isGateway,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 1L,
    )

    private fun mdns(
        serviceName: String,
        serviceType: String = "_http._tcp",
        hostname: String? = null,
        port: Int? = null,
    ) = LanMdnsObservation(
        serviceName = serviceName,
        serviceType = serviceType,
        hostname = hostname,
        port = port,
        observedAt = 1L,
    )

    private fun upnp(
        friendlyName: String? = null,
        manufacturer: String? = null,
        modelName: String? = null,
        modelNumber: String? = null,
        deviceType: String? = null,
        services: List<LanUpnpService> = emptyList(),
    ) = LanUpnpObservation(
        friendlyName = friendlyName,
        manufacturer = manufacturer,
        modelName = modelName,
        modelNumber = modelNumber,
        deviceType = deviceType,
        services = services,
        observedAt = 1L,
    )
}
