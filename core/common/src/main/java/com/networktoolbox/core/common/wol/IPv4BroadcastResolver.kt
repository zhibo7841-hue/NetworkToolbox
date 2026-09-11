package com.networktoolbox.core.common.wol

import com.networktoolbox.core.common.ipv4.IPv4Address

sealed interface IPv4BroadcastResolution {
    data class Available(val address: String) : IPv4BroadcastResolution

    data class Unavailable(val reason: IPv4BroadcastUnavailableReason) : IPv4BroadcastResolution
}

enum class IPv4BroadcastUnavailableReason {
    INVALID_ADDRESS,
    INVALID_PREFIX,
    NO_DIRECTED_BROADCAST,
}

/** Calculates the directed broadcast from the current IPv4 address and prefix. */
object IPv4BroadcastResolver {
    fun resolve(ipv4Address: String?, prefixLength: Int?): IPv4BroadcastResolution {
        val address = IPv4Address.parse(ipv4Address.orEmpty())
            ?: return IPv4BroadcastResolution.Unavailable(
                IPv4BroadcastUnavailableReason.INVALID_ADDRESS,
            )
        val prefix = prefixLength ?: return IPv4BroadcastResolution.Unavailable(
            IPv4BroadcastUnavailableReason.INVALID_PREFIX,
        )
        if (prefix !in 0..32) {
            return IPv4BroadcastResolution.Unavailable(
                IPv4BroadcastUnavailableReason.INVALID_PREFIX,
            )
        }
        if (prefix >= 31) {
            return IPv4BroadcastResolution.Unavailable(
                IPv4BroadcastUnavailableReason.NO_DIRECTED_BROADCAST,
            )
        }

        val hostMask = if (prefix == 0) {
            0xFFFFFFFFL
        } else {
            (1L shl (32 - prefix)) - 1L
        }
        val broadcast = (address.value or hostMask) and 0xFFFFFFFFL
        return IPv4BroadcastResolution.Available(
            IPv4Address.parse(
                listOf(
                    (broadcast ushr 24) and 0xFF,
                    (broadcast ushr 16) and 0xFF,
                    (broadcast ushr 8) and 0xFF,
                    broadcast and 0xFF,
                ).joinToString("."),
            )!!.toDottedDecimal(),
        )
    }
}
