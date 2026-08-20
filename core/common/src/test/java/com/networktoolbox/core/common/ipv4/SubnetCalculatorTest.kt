package com.networktoolbox.core.common.ipv4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubnetCalculatorTest {
    @Test
    fun calculatesClassCSubnet() {
        val result = SubnetCalculator.calculate("192.168.1.100/24")

        assertSubnet(
            result = result,
            ipAddress = "192.168.1.100",
            prefixLength = 24,
            subnetMask = "255.255.255.0",
            networkAddress = "192.168.1.0",
            broadcastAddress = "192.168.1.255",
            usableRangeStart = "192.168.1.1",
            usableRangeEnd = "192.168.1.254",
            hostCount = 254L,
        )
    }

    @Test
    fun calculatesClassASubnet() {
        val result = SubnetCalculator.calculate("10.0.0.1/8")

        assertEquals("255.0.0.0", result.subnetMask)
        assertEquals("10.0.0.0", result.networkAddress)
        assertEquals("10.255.255.255", result.broadcastAddress)
        assertEquals("10.0.0.1", result.usableRangeStart)
        assertEquals("10.255.255.254", result.usableRangeEnd)
        assertEquals(16_777_214L, result.hostCount)
    }

    @Test
    fun calculatesClassBSubnet() {
        val result = SubnetCalculator.calculate("172.16.5.20/16")

        assertEquals("255.255.0.0", result.subnetMask)
        assertEquals("172.16.0.0", result.networkAddress)
        assertEquals("172.16.255.255", result.broadcastAddress)
        assertEquals(65_534L, result.hostCount)
    }

    @Test
    fun calculatesZeroPrefixBoundary() {
        val result = SubnetCalculator.calculate("0.0.0.0/0")

        assertSubnet(
            result = result,
            ipAddress = "0.0.0.0",
            prefixLength = 0,
            subnetMask = "0.0.0.0",
            networkAddress = "0.0.0.0",
            broadcastAddress = "255.255.255.255",
            usableRangeStart = "0.0.0.1",
            usableRangeEnd = "255.255.255.254",
            hostCount = 4_294_967_294L,
        )
    }

    @Test
    fun calculatesSingleAddressBoundary() {
        val result = SubnetCalculator.calculate("255.255.255.255/32")

        assertSubnet(
            result = result,
            ipAddress = "255.255.255.255",
            prefixLength = 32,
            subnetMask = "255.255.255.255",
            networkAddress = "255.255.255.255",
            broadcastAddress = "255.255.255.255",
            usableRangeStart = "255.255.255.255",
            usableRangeEnd = "255.255.255.255",
            hostCount = 1L,
        )
    }

    @Test
    fun treatsThirtyOnePrefixAsTwoPointToPointHosts() {
        val result = SubnetCalculator.calculate("192.0.2.1/31")

        assertEquals("192.0.2.0", result.networkAddress)
        assertEquals("192.0.2.1", result.broadcastAddress)
        assertEquals("192.0.2.0", result.usableRangeStart)
        assertEquals("192.0.2.1", result.usableRangeEnd)
        assertEquals(2L, result.hostCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeOctet() {
        SubnetCalculator.calculate("999.999.999.999/24")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrefixAboveThirtyTwo() {
        SubnetCalculator.calculate("192.168.1.1/33")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrefixForty() {
        SubnetCalculator.calculate("192.168.1.1/40")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericAddress() {
        SubnetCalculator.calculate("abc/24")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingCidrSeparator() {
        SubnetCalculator.calculate("192.168.1.1")
    }

    @Test
    fun parserReturnsNullForMalformedAddress() {
        assertNull(IPv4Address.parse("192.168.1"))
        assertNull(IPv4Address.parse("192.168.1.256"))
    }

    private fun assertSubnet(
        result: SubnetResult,
        ipAddress: String,
        prefixLength: Int,
        subnetMask: String,
        networkAddress: String,
        broadcastAddress: String,
        usableRangeStart: String,
        usableRangeEnd: String,
        hostCount: Long,
    ) {
        assertEquals(ipAddress, result.ipAddress)
        assertEquals(prefixLength, result.prefixLength)
        assertEquals(subnetMask, result.subnetMask)
        assertEquals(networkAddress, result.networkAddress)
        assertEquals(broadcastAddress, result.broadcastAddress)
        assertEquals(usableRangeStart, result.usableRangeStart)
        assertEquals(usableRangeEnd, result.usableRangeEnd)
        assertEquals(hostCount, result.hostCount)
    }
}
