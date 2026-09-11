package com.networktoolbox.core.common.wol

/** Builds the standard 6 x FF preamble followed by sixteen MAC repetitions. */
object MagicPacketBuilder {
    const val PACKET_SIZE = 102
    private const val PREAMBLE_SIZE = 6
    private const val MAC_REPETITIONS = 16

    fun build(macAddress: MacAddress): ByteArray {
        val packet = ByteArray(PACKET_SIZE) { index ->
            if (index < PREAMBLE_SIZE) 0xFF.toByte() else 0
        }
        val mac = macAddress.toByteArray()
        repeat(MAC_REPETITIONS) { repetition ->
            mac.copyInto(
                destination = packet,
                destinationOffset = PREAMBLE_SIZE + repetition * mac.size,
            )
        }
        return packet
    }
}
