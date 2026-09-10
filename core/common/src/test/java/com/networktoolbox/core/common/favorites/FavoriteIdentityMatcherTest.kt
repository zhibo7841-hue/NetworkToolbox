package com.networktoolbox.core.common.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityMatcherTest {
    @Test
    fun `valid mac wins over protocol and ip`() {
        val candidate = candidate(mac = "aa:bb:cc:dd:ee:ff", protocol = "uuid:router", ip = "10.0.1.20")

        assertEquals(
            FavoriteDeviceIdentity(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF"),
            candidate.identity,
        )
    }

    @Test
    fun `mac formats normalize to the same identity`() {
        assertEquals(
            FavoriteIdentityMatcher.normalizeMac("AA-BB-CC-DD-EE-FF"),
            FavoriteIdentityMatcher.normalizeMac("aabb.ccdd.eeff"),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            FavoriteIdentityMatcher.normalizeMac("aabbccddeeff"),
        )
    }

    @Test
    fun `placeholder and malformed macs are ignored`() {
        listOf(
            "00:00:00:00:00:00",
            "ff:ff:ff:ff:ff:ff",
            "02:00:00:00:00:00",
            "not-a-mac",
            "AA:BB:CC:DD:EE",
        ).forEach { value -> assertNull(FavoriteIdentityMatcher.normalizeMac(value)) }
    }

    @Test
    fun `protocol is used when mac is unavailable`() {
        val candidate = candidate(mac = null, protocol = " UUID:Device-1 ", ip = "10.0.1.20")

        assertEquals(
            FavoriteDeviceIdentity(FavoriteIdentityType.PROTOCOL, "uuid:device-1"),
            candidate.identity,
        )
    }

    @Test
    fun `ip is the weakest identity`() {
        val candidate = candidate(mac = null, protocol = null, ip = "010.0.1.20")

        assertEquals(FavoriteIdentityType.NETWORK_IP, candidate.identity.type)
        assertEquals("10.0.1.20", candidate.identity.value)
    }

    @Test
    fun `same valid mac in same scope matches`() {
        val saved = favorite(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF", mac = "AA:BB:CC:DD:EE:FF")

        assertTrue(FavoriteIdentityMatcher.matches(saved, candidate(mac = "aa-bb-cc-dd-ee-ff")))
    }

    @Test
    fun `different mac does not match even when ip is unchanged`() {
        val saved = favorite(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF", mac = "AA:BB:CC:DD:EE:FF")

        assertFalse(FavoriteIdentityMatcher.matches(saved, candidate(mac = "11:22:33:44:55:66")))
    }

    @Test
    fun `same ip in different scopes does not match`() {
        val saved = favorite(FavoriteIdentityType.NETWORK_IP, "10.0.1.20", scope = "scope-a", mac = null)

        assertFalse(
            FavoriteIdentityMatcher.matches(
                saved,
                candidate(scope = "scope-b", mac = null, protocol = null),
            ),
        )
    }

    @Test
    fun `weak identity matches only same scope and ip`() {
        val saved = favorite(FavoriteIdentityType.NETWORK_IP, "10.0.1.20", mac = null)

        assertTrue(FavoriteIdentityMatcher.matches(saved, candidate(mac = null, protocol = null)))
        assertFalse(
            FavoriteIdentityMatcher.matches(
                saved,
                candidate(ip = "10.0.1.21", mac = null, protocol = null),
            ),
        )
    }

    @Test
    fun `hostname is never used as identity`() {
        val saved = favorite(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF", mac = "AA:BB:CC:DD:EE:FF")

        assertFalse(
            FavoriteIdentityMatcher.matches(
                saved,
                candidate(ip = "10.0.1.99", mac = "11:22:33:44:55:66", protocol = null),
            ),
        )
    }

    @Test
    fun `saved strong identity does not fall back to ip`() {
        val saved = favorite(FavoriteIdentityType.MAC, "AA:BB:CC:DD:EE:FF", mac = "AA:BB:CC:DD:EE:FF")

        assertFalse(FavoriteIdentityMatcher.matches(saved, candidate(mac = null, protocol = null)))
    }

    @Test
    fun `saved protocol identity does not fall back to ip`() {
        val saved = favorite(FavoriteIdentityType.PROTOCOL, "uuid:device-1", mac = null)

        assertFalse(FavoriteIdentityMatcher.matches(saved, candidate(mac = null, protocol = null)))
        assertTrue(FavoriteIdentityMatcher.matches(saved, candidate(mac = null, protocol = "UUID:DEVICE-1")))
    }

    @Test
    fun `duplicate observations resolve to the same identity`() {
        val first = candidate(mac = "aa:bb:cc:dd:ee:ff", ip = "10.0.1.20")
        val second = candidate(mac = "AA-BB-CC-DD-EE-FF", ip = "10.0.1.21")

        assertEquals(first.identity, second.identity)
    }

    @Test
    fun `gateway and local roles do not change identity`() {
        val candidate = candidate(mac = null, protocol = null)

        assertEquals(FavoriteIdentityType.NETWORK_IP, candidate.identity.type)
    }

    @Test
    fun `network switch is an identity boundary`() {
        val saved = favorite(FavoriteIdentityType.NETWORK_IP, "10.0.1.20", scope = "wifi-a", mac = null)

        assertFalse(FavoriteIdentityMatcher.matches(saved, candidate(scope = "wifi-b", mac = null, protocol = null)))
    }

    private fun candidate(
        scope: String = "scope-a",
        ip: String = "10.0.1.20",
        mac: String? = "AA:BB:CC:DD:EE:FF",
        protocol: String? = null,
    ) = FavoriteDeviceCandidate(scope, ip, mac, protocol)

    private fun favorite(
        type: FavoriteIdentityType,
        value: String,
        scope: String = "scope-a",
        mac: String? = "AA:BB:CC:DD:EE:FF",
    ) = FavoriteDevice(
        identityType = type,
        identityValue = value,
        networkScope = scope,
        lastKnownIpv4 = "10.0.1.20",
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = mac,
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
    )
}
