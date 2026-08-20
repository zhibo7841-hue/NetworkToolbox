package com.networktoolbox.core.common.ipv4

object SubnetCalculator {
    private const val IPV4_BITS = 32
    private const val FULL_IPV4_MASK = 0xFFFF_FFFFL
    private const val INVALID_INPUT_MESSAGE = "Invalid IPv4/CIDR input"

    fun calculate(input: String): SubnetResult {
        val parts = input.trim().split('/')
        require(parts.size == 2) { INVALID_INPUT_MESSAGE }

        val ipAddress = IPv4Address.parse(parts[0].trim())
            ?: throw IllegalArgumentException(INVALID_INPUT_MESSAGE)
        val prefixLength = parts[1].trim().toIntOrNull()
            ?: throw IllegalArgumentException(INVALID_INPUT_MESSAGE)
        require(prefixLength in 0..IPV4_BITS) { INVALID_INPUT_MESSAGE }

        val hostBits = IPV4_BITS - prefixLength
        val subnetMask = if (prefixLength == 0) {
            0L
        } else {
            (FULL_IPV4_MASK shl hostBits) and FULL_IPV4_MASK
        }
        val networkAddress = ipAddress.value and subnetMask
        val hostMask = (1L shl hostBits) - 1L
        val broadcastAddress = networkAddress or hostMask

        val (hostCount, usableStart, usableEnd) = when {
            prefixLength <= 30 -> Triple(
                hostMask - 1L,
                networkAddress + 1L,
                broadcastAddress - 1L,
            )
            prefixLength == 31 -> Triple(
                2L,
                networkAddress,
                broadcastAddress,
            )
            else -> Triple(
                1L,
                networkAddress,
                networkAddress,
            )
        }

        return SubnetResult(
            ipAddress = ipAddress.toDottedDecimal(),
            prefixLength = prefixLength,
            subnetMask = IPv4Address(subnetMask).toDottedDecimal(),
            networkAddress = IPv4Address(networkAddress).toDottedDecimal(),
            broadcastAddress = IPv4Address(broadcastAddress).toDottedDecimal(),
            usableRangeStart = IPv4Address(usableStart).toDottedDecimal(),
            usableRangeEnd = IPv4Address(usableEnd).toDottedDecimal(),
            hostCount = hostCount,
        )
    }
}
