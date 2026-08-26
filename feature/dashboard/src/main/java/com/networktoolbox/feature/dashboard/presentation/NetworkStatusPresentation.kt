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

    fun dnsSummary(serverCount: Int): String = when {
        serverCount <= 0 -> "未配置"
        else -> "$serverCount 个服务器"
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
        context.ipv4Address != null || context.ipv6Address != null -> "已连接"
        context.connectionType == ConnectionType.UNKNOWN -> "未知"
        else -> "已连接"
    }

    private fun parseIpv6Literal(value: String): Inet6Address? {
        val addressWithoutScope = value.substringBefore('%')
        if (!addressWithoutScope.contains(':')) return null
        return runCatching {
            InetAddress.getByName(addressWithoutScope) as? Inet6Address
        }.getOrNull()
    }
}
