package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.ipv4.IPv4Address
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import java.security.MessageDigest
import java.util.Locale

/**
 * Produces an opaque, local-network-scoped key for favorite matching.
 *
 * The raw network facts are intentionally never persisted. The IPv4 prefix is
 * the primary scope boundary; other already-available facts reduce accidental
 * collisions without making an SSID the device identity.
 */
object LanNetworkScope {
    fun from(context: NetworkContext): String? {
        if (context.activeNetworkAvailable == false) return null
        if (context.connectionType != ConnectionType.WIFI &&
            context.connectionType != ConnectionType.ETHERNET
        ) {
            return null
        }

        val address = IPv4Address.parse(context.ipv4Address.orEmpty()) ?: return null
        val prefixLength = context.ipv4PrefixLength ?: return null
        if (prefixLength !in 0..32) return null

        val networkPrefix = networkPrefix(address.value, prefixLength)
        val canonicalParts = listOf(
            "v1",
            context.connectionType.name,
            "$networkPrefix/$prefixLength",
            canonicalAddress(context.gateway),
            context.interfaceName.orEmpty().trim().lowercase(Locale.ROOT),
            reliableWifiName(context.wifiName),
            context.dnsServers.mapNotNull(::canonicalAddress).sorted().joinToString(","),
        )
        return "v1:${sha256(canonicalParts.joinToString("|"))}"
    }

    private fun networkPrefix(value: Long, prefixLength: Int): String {
        val mask = if (prefixLength == 0) {
            0L
        } else {
            (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        }
        return IPv4Address.parse(
            listOf(
                ((value and mask) ushr 24) and 0xFF,
                ((value and mask) ushr 16) and 0xFF,
                ((value and mask) ushr 8) and 0xFF,
                (value and mask) and 0xFF,
            ).joinToString("."),
        )!!.toDottedDecimal()
    }

    private fun canonicalAddress(value: String?): String = value
        ?.trim()
        ?.let(IPv4Address::parse)
        ?.toDottedDecimal()
        ?: value.orEmpty().trim().lowercase(Locale.ROOT)

    private fun reliableWifiName(value: String?): String = value
        ?.trim()
        ?.takeUnless {
            it.isEmpty() || it.equals("<unknown ssid>", ignoreCase = true) ||
                it.equals("unknown ssid", ignoreCase = true) ||
                it.equals("unknown", ignoreCase = true)
        }
        ?.lowercase(Locale.ROOT)
        .orEmpty()

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}
