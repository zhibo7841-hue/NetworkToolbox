package com.networktoolbox.feature.dashboard.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import java.net.Inet6Address
import java.net.InetAddress

enum class Ipv6DisplayStatus {
    NOT_CONFIGURED,
    LINK_LOCAL_ONLY,
    CONFIGURED,
    UNKNOWN,
}

data class PrimaryAddressSummary(
    val label: String,
    val value: String,
)

object NetworkStatusPresentation {
    fun ipv6Addresses(context: NetworkContext): List<String> =
        (context.ipv6Addresses.ifEmpty { listOfNotNull(context.ipv6Address) })
            .filter(String::isNotBlank)
            .distinct()

    fun ipv6Status(context: NetworkContext): Ipv6DisplayStatus =
        ipv6Status(ipv6Addresses(context))

    fun ipv6Status(addresses: List<String>): Ipv6DisplayStatus {
        val normalizedAddresses = addresses.filter(String::isNotBlank)
        if (normalizedAddresses.isEmpty()) return Ipv6DisplayStatus.NOT_CONFIGURED

        val parsedAddresses = normalizedAddresses.mapNotNull(::parseIpv6Literal)
        if (parsedAddresses.size != normalizedAddresses.size) return Ipv6DisplayStatus.UNKNOWN
        return if (parsedAddresses.all { it.isLinkLocalAddress }) {
            Ipv6DisplayStatus.LINK_LOCAL_ONLY
        } else {
            Ipv6DisplayStatus.CONFIGURED
        }
    }

    fun ipv6Label(status: Ipv6DisplayStatus): String = when (status) {
        Ipv6DisplayStatus.NOT_CONFIGURED -> "未配置"
        Ipv6DisplayStatus.LINK_LOCAL_ONLY -> "仅链路本地"
        Ipv6DisplayStatus.CONFIGURED -> "已配置"
        Ipv6DisplayStatus.UNKNOWN -> "未知"
    }

    fun ipv4PrefixToNetmask(prefixLength: Int?): String? {
        if (prefixLength == null || prefixLength !in 0..32) return null

        val mask = if (prefixLength == 0) {
            0L
        } else {
            (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        }
        return (3 downTo 0).joinToString(".") { index ->
            ((mask shr (index * 8)) and 0xFF).toString()
        }
    }

    fun preferredDnsForSummary(dnsServers: List<String>): String? {
        val configuredServers = dnsServers.filter(String::isNotBlank)
        return configuredServers.firstOrNull(::isIpv4Literal)
            ?: configuredServers.firstOrNull(::isIpv6Literal)
    }

    fun primaryAddressForSummary(context: NetworkContext): PrimaryAddressSummary {
        context.ipv4Address
            ?.takeIf(String::isNotBlank)
            ?.let { return PrimaryAddressSummary("IPv4 地址", it) }

        val ipv6Addresses = ipv6Addresses(context)
        ipv6Addresses.firstOrNull { address ->
            parseIpv6Literal(address)?.isLinkLocalAddress == false
        }?.let { return PrimaryAddressSummary("IPv6 地址", it) }

        return when (ipv6Status(ipv6Addresses)) {
            Ipv6DisplayStatus.LINK_LOCAL_ONLY ->
                PrimaryAddressSummary("IPv6", "仅链路本地")

            Ipv6DisplayStatus.UNKNOWN -> PrimaryAddressSummary("IPv6", "未知")
            Ipv6DisplayStatus.NOT_CONFIGURED,
            Ipv6DisplayStatus.CONFIGURED,
            -> PrimaryAddressSummary("IPv4 地址", "未配置")
        }
    }

    fun shouldShowGateway(context: NetworkContext): Boolean =
        context.connectionType == ConnectionType.WIFI ||
            context.connectionType == ConnectionType.ETHERNET

    fun shouldShowWifiSignal(context: NetworkContext): Boolean =
        context.connectionType == ConnectionType.WIFI

    fun connectionStatus(context: NetworkContext): String = when {
        context.activeNetworkAvailable == false -> "未连接"
        context.activeNetworkAvailable == null &&
            context.connectionType == ConnectionType.UNKNOWN -> "未知"
        context.activeNetworkAvailable == true -> "已连接"
        context.ipv4Address != null || ipv6Addresses(context).isNotEmpty() -> "已连接"
        context.connectionType == ConnectionType.UNKNOWN -> "未知"
        else -> "已连接"
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.substringBefore('%').split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isIpv6Literal(value: String): Boolean = parseIpv6Literal(value) != null

    private fun parseIpv6Literal(value: String): Inet6Address? {
        val addressWithoutScope = value.substringBefore('%')
        if (!addressWithoutScope.contains(':')) return null
        return runCatching {
            InetAddress.getByName(addressWithoutScope) as? Inet6Address
        }.getOrNull()
    }
}
