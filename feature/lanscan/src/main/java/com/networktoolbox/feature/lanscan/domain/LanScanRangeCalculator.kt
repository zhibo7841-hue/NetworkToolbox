package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason

class LanScanRangeCalculator(
    private val maxDefaultPrefixLength: Int = DEFAULT_MAX_DEFAULT_PREFIX_LENGTH,
) {
    init {
        require(maxDefaultPrefixLength in 0..32)
    }

    fun calculate(context: NetworkContext): LanScanRangeResult {
        if (context.activeNetworkAvailable == false) {
            return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.NO_ACTIVE_NETWORK,
                message = "No active network is available.",
            )
        }
        if (context.vpnActive == true || context.connectionType == ConnectionType.VPN) {
            return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.VPN_BLOCKED,
                message = "LAN scanning is not enabled for VPN networks.",
            )
        }
        if (context.connectionType !in setOf(ConnectionType.WIFI, ConnectionType.ETHERNET)) {
            return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.UNSUPPORTED_NETWORK,
                message = "LAN scanning requires Wi-Fi or Ethernet.",
            )
        }

        val address = context.ipv4Address?.toIpv4NumberOrNull()
            ?: return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.NO_IPV4_ADDRESS,
                message = "No IPv4 address is available for the local network.",
            )
        val originalPrefix = context.ipv4PrefixLength
            ?.takeIf { it in 0..32 }
            ?: return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.INVALID_PREFIX,
                message = "The IPv4 prefix length is unavailable or invalid.",
            )
        if (originalPrefix >= 31) {
            return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.SPECIAL_PREFIX,
                message = "Point-to-point and host-only prefixes are not scanned automatically.",
            )
        }
        if (!address.isPrivateOrLinkLocal()) {
            return LanScanRangeResult.Rejected(
                reason = LanScanRejectionReason.NON_LOCAL_RANGE,
                message = "The IPv4 range is not clearly a local network.",
            )
        }

        val original = address.toRange(originalPrefix)
        val selectedPrefix = maxOf(originalPrefix, maxDefaultPrefixLength)
        val selectedAddress = address.toRange(selectedPrefix)
        val limited = selectedPrefix != originalPrefix
        val selectedHostCount = selectedAddress.hostCount()
        return LanScanRangeResult.Ready(
            LanScanRange(
                networkAddress = selectedAddress.network.toIpv4String(),
                broadcastAddress = selectedAddress.broadcast.toIpv4String(),
                firstHost = (selectedAddress.network + 1L).toIpv4String(),
                lastHost = (selectedAddress.broadcast - 1L).toIpv4String(),
                hostCount = selectedHostCount.toInt(),
                prefixLength = selectedPrefix,
                originalNetworkAddress = original.network.toIpv4String(),
                originalBroadcastAddress = original.broadcast.toIpv4String(),
                originalHostCount = original.hostCount(),
                originalPrefixLength = originalPrefix,
                rangeWasLimited = limited,
            ),
        )
    }

    private data class NumericRange(
        val network: Long,
        val broadcast: Long,
    ) {
        fun hostCount(): Long = (broadcast - network - 1L).coerceAtLeast(0L)
    }

    private fun Long.toRange(prefixLength: Int): NumericRange {
        val mask = when (prefixLength) {
            0 -> 0L
            else -> (0xFFFF_FFFFL shl (32 - prefixLength)) and 0xFFFF_FFFFL
        }
        val network = this and mask
        val broadcast = network or (mask.inv() and 0xFFFF_FFFFL)
        return NumericRange(network, broadcast)
    }

    private fun Long.isPrivateOrLinkLocal(): Boolean = when {
        this in 0x0A00_0000L..0x0AFF_FFFFL -> true // 10.0.0.0/8
        this in 0xAC10_0000L..0xAC1F_FFFFL -> true // 172.16.0.0/12
        this in 0xC0A8_0000L..0xC0A8_FFFFL -> true // 192.168.0.0/16
        this in 0xA9FE_0000L..0xA9FE_FFFFL -> true // 169.254.0.0/16
        else -> false
    }

    private companion object {
        const val DEFAULT_MAX_DEFAULT_PREFIX_LENGTH = 24
    }
}

private fun String.toIpv4NumberOrNull(): Long? {
    val parts = trim().split('.')
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        part.toIntOrNull()?.takeIf { it in 0..255 }
    }
    if (octets.any { it == null }) return null
    return octets.filterNotNull().fold(0L) { result, octet ->
        (result shl 8) or octet.toLong()
    }
}

private fun Long.toIpv4String(): String = listOf(
    (this ushr 24) and 0xFF,
    (this ushr 16) and 0xFF,
    (this ushr 8) and 0xFF,
    this and 0xFF,
).joinToString(".")
