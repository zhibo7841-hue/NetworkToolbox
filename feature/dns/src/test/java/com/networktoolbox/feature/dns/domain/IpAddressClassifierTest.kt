package com.networktoolbox.feature.dns.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpAddressClassifierTest {
    @Test
    fun recognizesFakeIpRange() {
        assertEquals(IpAddressKind.FAKE_IP_RANGE, IpAddressClassifier.classify("198.18.2.10"))
        assertEquals(IpAddressKind.FAKE_IP_RANGE, IpAddressClassifier.classify("198.19.255.254"))
    }

    @Test
    fun recognizesCommonPrivateAndSpecialIpv4Ranges() {
        assertEquals(IpAddressKind.RFC1918_PRIVATE, IpAddressClassifier.classify("10.0.0.1"))
        assertEquals(IpAddressKind.RFC1918_PRIVATE, IpAddressClassifier.classify("172.16.0.1"))
        assertEquals(IpAddressKind.RFC1918_PRIVATE, IpAddressClassifier.classify("192.168.1.1"))
        assertEquals(IpAddressKind.LOOPBACK, IpAddressClassifier.classify("127.0.0.1"))
        assertEquals(IpAddressKind.LINK_LOCAL, IpAddressClassifier.classify("169.254.1.1"))
    }

    @Test
    fun recognizesSpecialIpv6RangesAndLeavesPublicAddressUnclassified() {
        assertEquals(IpAddressKind.IPV6_ULA, IpAddressClassifier.classify("fd00::1"))
        assertEquals(IpAddressKind.IPV6_LINK_LOCAL, IpAddressClassifier.classify("fe80::1%wlan0"))
        assertEquals(IpAddressKind.LOOPBACK, IpAddressClassifier.classify("::1"))
        assertNull(IpAddressClassifier.classify("2001:db8::1"))
    }
}
