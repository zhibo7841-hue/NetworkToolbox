package com.networktoolbox.core.common.wol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanCoreTest {
    @Test
    fun `mac parser accepts supported formats and canonicalizes`() {
        val expected = MacAddress.parse("00:11:22:33:44:55")

        assertEquals(expected, MacAddress.parse("00-11-22-33-44-55"))
        assertEquals(expected, MacAddress.parse("001122334455"))
        assertEquals(expected, MacAddress.parse(" 00:11:22:33:44:55 "))
        assertEquals("00:11:22:33:44:55", expected.toString())
        assertEquals(
            "02:11:22:33:44:55",
            MacAddress.parse("021122334455")?.toString(),
        )
    }

    @Test
    fun `mac parser rejects invalid and non unicast addresses`() {
        listOf(
            "",
            "00112233445",
            "00112233445566",
            "00:11:22:33:44",
            "00:11:22:33:44:GG",
            "00:11:22:33:44:55:66",
            "000000000000",
            "FF:FF:FF:FF:FF:FF",
            "01:11:22:33:44:55",
            "00-11:22-33:44-55",
        ).forEach { input ->
            assertEquals("Expected invalid MAC: $input", null, MacAddress.parse(input))
        }
    }

    @Test
    fun `magic packet is exactly six ff bytes and sixteen mac repetitions`() {
        val mac = MacAddress.parse("00:11:22:33:44:55")!!
        val packet = MagicPacketBuilder.build(mac)

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        repeat(16) { repetition ->
            assertArrayEquals(
                mac.toByteArray(),
                packet.copyOfRange(6 + repetition * 6, 6 + (repetition + 1) * 6),
            )
        }
    }

    @Test
    fun `wake on lan config validates port boundaries`() {
        val mac = MacAddress.parse("00:11:22:33:44:55")!!
        assertEquals(9, WakeOnLanConfig(mac).udpPort)
        assertTrue(runCatching { WakeOnLanConfig(mac, 1) }.isSuccess)
        assertTrue(runCatching { WakeOnLanConfig(mac, 65535) }.isSuccess)
        assertTrue(runCatching { WakeOnLanConfig(mac, 0) }.isFailure)
        assertTrue(runCatching { WakeOnLanConfig(mac, 65536) }.isFailure)
    }

    @Test
    fun `directed broadcast is calculated for common cidrs`() {
        assertEquals(
            IPv4BroadcastResolution.Available("192.168.1.255"),
            IPv4BroadcastResolver.resolve("192.168.1.20", 24),
        )
        assertEquals(
            IPv4BroadcastResolution.Available("10.0.255.255"),
            IPv4BroadcastResolver.resolve("10.0.1.20", 16),
        )
        assertEquals(
            IPv4BroadcastResolution.Available("255.255.255.255"),
            IPv4BroadcastResolver.resolve("10.0.1.20", 0),
        )
    }

    @Test
    fun `directed broadcast is unavailable for point to point prefixes`() {
        assertEquals(
            IPv4BroadcastResolution.Unavailable(
                IPv4BroadcastUnavailableReason.NO_DIRECTED_BROADCAST,
            ),
            IPv4BroadcastResolver.resolve("192.168.1.1", 31),
        )
        assertEquals(
            IPv4BroadcastResolution.Unavailable(
                IPv4BroadcastUnavailableReason.NO_DIRECTED_BROADCAST,
            ),
            IPv4BroadcastResolver.resolve("192.168.1.1", 32),
        )
    }
}
