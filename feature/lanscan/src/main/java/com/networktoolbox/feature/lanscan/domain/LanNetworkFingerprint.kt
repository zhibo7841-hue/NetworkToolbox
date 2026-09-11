package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.NetworkContext
import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity for the network context to which a scan is bound.
 *
 * Eligible Wi-Fi/Ethernet networks reuse [LanNetworkScope], the existing
 * opaque scope used by saved-device matching. Other contexts still receive a
 * deterministic, opaque fingerprint so a change to or from an unsupported
 * network cannot leave an old scan looking current. The host IPv4 address is
 * deliberately not the only input to this identity.
 */
object LanNetworkFingerprint {
    fun from(context: NetworkContext): String {
        LanNetworkScope.from(context)?.let { scope ->
            return "lan:$scope"
        }

        val canonical = listOf(
            "v1",
            context.activeNetworkAvailable.toString(),
            context.connectionType.name,
            context.ipv4Address.orEmpty().trim(),
            context.ipv4PrefixLength?.toString().orEmpty(),
            context.ipv6Addresses.map(String::trim).filter(String::isNotBlank)
                .sorted()
                .joinToString(","),
            context.ipv6Address.orEmpty().trim(),
            context.gateway.orEmpty().trim(),
            context.interfaceName.orEmpty().trim().lowercase(Locale.ROOT),
            context.vpnActive.toString(),
            context.dnsServers.map(String::trim).filter(String::isNotBlank)
                .sorted()
                .joinToString(","),
            context.privateDnsActive.toString(),
            context.privateDnsServerName.orEmpty().trim().lowercase(Locale.ROOT),
        ).joinToString("|")

        return "context:${sha256(canonical)}"
    }

    fun matches(first: NetworkContext, second: NetworkContext): Boolean =
        from(first) == from(second)

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
}
