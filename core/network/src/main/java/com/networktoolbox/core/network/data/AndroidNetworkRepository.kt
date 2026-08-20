package com.networktoolbox.core.network.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class AndroidNetworkRepository(context: Context) : NetworkRepository {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.getSystemService(WifiManager::class.java)

    override fun observeNetworkContext(): Flow<NetworkContext> {
        val manager = connectivityManager ?: return flowOf(NetworkContext.unknown())

        return callbackFlow {
            trySend(readCurrentContext(manager))

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(readContext(manager, network))
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    trySend(readContext(manager, network, networkCapabilities))
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    trySend(readContext(manager, network, linkProperties = linkProperties))
                }

                override fun onLost(network: Network) {
                    trySend(readCurrentContext(manager))
                }
            }

            try {
                manager.registerDefaultNetworkCallback(callback)
            } catch (_: SecurityException) {
                trySend(NetworkContext.unknown())
                close()
            } catch (_: RuntimeException) {
                trySend(NetworkContext.unknown())
                close()
            }

            awaitClose {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }.distinctUntilChanged()
    }

    private fun readCurrentContext(manager: ConnectivityManager): NetworkContext =
        readContext(manager, manager.activeNetwork)

    private fun readContext(
        manager: ConnectivityManager,
        network: Network?,
        networkCapabilities: NetworkCapabilities? = null,
        linkProperties: LinkProperties? = null,
    ): NetworkContext {
        if (network == null) return NetworkContext.unknown()

        return try {
            val capabilities = networkCapabilities ?: manager.getNetworkCapabilities(network)
            val properties = linkProperties ?: manager.getLinkProperties(network)
            val wifiInfo = capabilities?.transportInfo as? WifiInfo

            NetworkContextMapper.map(
                NetworkContextSnapshot(
                    connectionType = connectionType(capabilities),
                    ipv4Address = properties?.findAddress(isIpv4 = true),
                    ipv6Address = properties?.findAddress(isIpv4 = false),
                    gateway = properties?.findDefaultGateway(),
                    dnsServers = properties?.dnsServers.orEmpty().mapNotNull(::hostAddress),
                    vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    wifiName = wifiInfo?.ssid
                        ?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }
                        ?.trim('"'),
                    wifiSignalLevel = wifiInfo?.rssi
                        ?.takeIf { it > -127 }
                        ?.let { wifiManager?.calculateSignalLevel(it) },
                ),
            )
        } catch (_: SecurityException) {
            NetworkContext.unknown()
        } catch (_: RuntimeException) {
            NetworkContext.unknown()
        }
    }

    private fun connectionType(capabilities: NetworkCapabilities?): ConnectionType {
        if (capabilities == null) return ConnectionType.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ->
                ConnectionType.BLUETOOTH
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.UNKNOWN
        }
    }

    private fun LinkProperties.findDefaultGateway(): String? =
        routes.firstOrNull(RouteInfo::isDefaultRoute)?.gateway?.let(::hostAddress)

    private fun LinkProperties.findAddress(isIpv4: Boolean): String? =
        linkAddresses
            .map { it.address }
            .firstOrNull { address ->
                if (isIpv4) address is java.net.Inet4Address else address is java.net.Inet6Address
            }
            ?.let(::hostAddress)

    private fun hostAddress(address: java.net.InetAddress): String? =
        address.hostAddress?.substringBefore('%')
}
