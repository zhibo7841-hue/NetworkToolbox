package com.networktoolbox.feature.dns.domain

enum class IpAddressKind {
    FAKE_IP_RANGE,
    RFC1918_PRIVATE,
    LOOPBACK,
    LINK_LOCAL,
    IPV6_ULA,
    IPV6_LINK_LOCAL,
}

data class IpAddressClassification(
    val address: String,
    val kind: IpAddressKind,
)

object IpAddressClassifier {
    fun classifyAll(addresses: Iterable<String>): List<IpAddressClassification> = addresses
        .mapNotNull { address ->
            classify(address)?.let { kind -> IpAddressClassification(address, kind) }
        }
        .distinct()

    fun classify(address: String): IpAddressKind? {
        val normalized = address.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        parseIpv4(normalized)?.let { octets ->
            val first = octets[0]
            val second = octets[1]
            return when {
                first == 198 && second in 18..19 -> IpAddressKind.FAKE_IP_RANGE
                first == 10 || (first == 172 && second in 16..31) ||
                    (first == 192 && second == 168) -> IpAddressKind.RFC1918_PRIVATE
                first == 127 -> IpAddressKind.LOOPBACK
                first == 169 && second == 254 -> IpAddressKind.LINK_LOCAL
                else -> null
            }
        }

        if (!normalized.contains(':')) return null
        if (normalized == "::1") return IpAddressKind.LOOPBACK
        val firstHextet = normalized.substringBefore(':').toIntOrNull(16) ?: return null
        return when {
            firstHextet in 0xfc00..0xfdff -> IpAddressKind.IPV6_ULA
            firstHextet in 0xfe80..0xfebf -> IpAddressKind.IPV6_LINK_LOCAL
            else -> null
        }
    }

    private fun parseIpv4(value: String): List<Int>? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part -> part.toIntOrNull() ?: return null }
        return octets.takeIf { values -> values.all { it in 0..255 } }
    }
}
