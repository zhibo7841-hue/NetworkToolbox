package com.networktoolbox.core.network

import com.networktoolbox.core.network.data.DefaultGatewayCandidate
import com.networktoolbox.core.network.data.DefaultGatewaySelector
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultGatewaySelectorTest {
    @Test
    fun dualStack_prefersIpv4RegardlessOfRouteOrder() {
        val candidates = listOf(
            DefaultGatewayCandidate(address = "fe80::1", isIpv4 = false),
            DefaultGatewayCandidate(address = "192.168.1.1", isIpv4 = true),
        )

        assertEquals("192.168.1.1", DefaultGatewaySelector.select(candidates))
        assertEquals(
            "192.168.1.1",
            DefaultGatewaySelector.select(candidates.reversed()),
        )
    }

    @Test
    fun ipv4Only_selectsIpv4Gateway() {
        assertEquals(
            "192.168.1.1",
            DefaultGatewaySelector.select(
                listOf(DefaultGatewayCandidate("192.168.1.1", isIpv4 = true)),
            ),
        )
    }

    @Test
    fun ipv6Only_keepsCandidateForSafeDiagnosticHandling() {
        assertEquals(
            "fe80::1",
            DefaultGatewaySelector.select(
                listOf(DefaultGatewayCandidate("fe80::1", isIpv4 = false)),
            ),
        )
    }
}
