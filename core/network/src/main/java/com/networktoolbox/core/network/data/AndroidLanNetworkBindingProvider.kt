package com.networktoolbox.core.network.data

import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.wol.LanNetworkBinding
import com.networktoolbox.core.network.wol.LanNetworkBindingProvider
import java.net.DatagramSocket

/**
 * Selects a fresh physical Wi-Fi/Ethernet Network for LAN UDP traffic.
 *
 * Android may expose a VPN as the active network. WoL must use the physical
 * LAN instead, so this provider deliberately filters VPN transports and binds
 * the socket to the selected Android Network before sending.
 */
class AndroidLanNetworkBindingProvider(context: Context) : LanNetworkBindingProvider {
    private val contextReader = AndroidNetworkContextReader(context)

    override fun current(): LanNetworkBinding? {
        val manager = contextReader.connectivityManager ?: return null
        val activeNetwork = manager.activeNetwork
        val networks = manager.allNetworks
            .asSequence()
            .distinct()
            .sortedByDescending { network -> network == activeNetwork }
            .mapNotNull { network -> physicalBinding(manager, network) }
            .toList()
        return networks.firstOrNull()
    }

    private fun physicalBinding(
        manager: android.net.ConnectivityManager,
        network: Network,
    ): LanNetworkBinding? = runCatching {
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val context = contextReader.readContext(network, networkCapabilities = capabilities)
        if (context.connectionType != ConnectionType.WIFI &&
            context.connectionType != ConnectionType.ETHERNET
        ) {
            return null
        }
        AndroidLanNetworkBinding(network, context)
    }.getOrNull()

    private class AndroidLanNetworkBinding(
        private val network: Network,
        override val networkContext: com.networktoolbox.core.network.model.NetworkContext,
    ) : LanNetworkBinding {
        override fun bindDatagramSocket(socket: DatagramSocket) {
            network.bindSocket(socket)
        }
    }
}
