package com.networktoolbox.core.network.traceroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TracerouteValidationTest {
    @Test
    fun defaultRequestUsesBoundedIpv4Settings() {
        val request = TracerouteRequest(target = "1.1.1.1")

        assertEquals(30, request.maxHops)
        assertEquals(3, request.probesPerHop)
        assertEquals(1_500, request.timeoutMs)
        assertEquals(TracerouteAddressFamily.IPV4, request.addressFamily)
        assertNull(TracerouteValidation.validate(request))
    }

    @Test
    fun ipv4LiteralIsNormalizedAndFakeIpIsOnlyFlagged() {
        assertEquals("1.2.3.4", TracerouteValidation.normalizeIpv4Literal("001.002.003.004"))
        assertTrue(TracerouteFakeIpDetector.isFakeIp("198.18.0.1"))
        assertTrue(TracerouteFakeIpDetector.isFakeIp("198.19.255.254"))
        assertFalse(TracerouteFakeIpDetector.isFakeIp("198.20.0.1"))
    }

    @Test
    fun invalidIpv4AndUnsupportedInputsAreRejected() {
        assertEquals(
            "Invalid IPv4 address or hostname.",
            TracerouteValidation.validate(TracerouteRequest("999.999.999.999")),
        )
        assertEquals(
            "IPv6 traceroute is not supported in Phase 1.",
            TracerouteValidation.validate(TracerouteRequest("2001:db8::1")),
        )
        assertEquals(
            "Maximum hops must be between 1 and 30.",
            TracerouteValidation.validate(TracerouteRequest("1.1.1.1", maxHops = 31)),
        )
        assertEquals(
            "Probes per hop must be between 1 and 3.",
            TracerouteValidation.validate(TracerouteRequest("1.1.1.1", probesPerHop = 4)),
        )
        assertEquals(
            "Traceroute must use a high UDP destination port.",
            TracerouteValidation.validate(TracerouteRequest("1.1.1.1", destinationPort = 53)),
        )
    }

    @Test
    fun hostnameValidationAcceptsHostnamesButNotMalformedLabels() {
        assertTrue(TracerouteValidation.isValidHostname("example.com"))
        assertTrue(TracerouteValidation.isValidHostname("router.local"))
        assertFalse(TracerouteValidation.isValidHostname("abc..123"))
        assertFalse(TracerouteValidation.isValidHostname("-example.com"))
        assertFalse(TracerouteValidation.isValidHostname("example.com."))
    }
}
