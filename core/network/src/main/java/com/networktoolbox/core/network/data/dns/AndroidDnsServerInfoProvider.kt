package com.networktoolbox.core.network.data.dns

import android.content.Context
import android.net.ConnectivityManager
import com.networktoolbox.core.network.dns.DnsServerInfo
import com.networktoolbox.core.network.dns.DnsServerInfoProvider

class AndroidDnsServerInfoProvider(
    context: Context,
    private val connectivityManager: ConnectivityManager =
        requireNotNull(context.getSystemService(ConnectivityManager::class.java)),
) : DnsServerInfoProvider {
    override fun current(): DnsServerInfo? = try {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        DnsServerInfo(
            configuredAddresses = linkProperties.dnsServers
                .mapNotNull { it.hostAddress ?: it.hostName }
                .distinct(),
            privateDnsActive = linkProperties.isPrivateDnsActive,
            privateDnsServerName = linkProperties.privateDnsServerName,
            actualResponder = null,
        )
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
