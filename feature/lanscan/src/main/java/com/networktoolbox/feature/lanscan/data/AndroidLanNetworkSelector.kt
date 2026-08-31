package com.networktoolbox.feature.lanscan.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.feature.lanscan.domain.SsdpDiscoveryRequest
import com.networktoolbox.feature.lanscan.domain.UpnpDescriptionRequest

/** Selects the current physical LAN Network for bounded enrichment traffic. */
internal class AndroidLanNetworkSelector(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun select(request: SsdpDiscoveryRequest): Network? = select(
        connectionType = request.connectionType,
        interfaceName = request.interfaceName,
    )

    fun select(request: UpnpDescriptionRequest): Network? = select(
        connectionType = request.connectionType,
        interfaceName = request.interfaceName,
    )

    @SuppressLint("MissingPermission")
    private fun select(
        connectionType: ConnectionType,
        interfaceName: String?,
    ): Network? {
        val manager = connectivityManager ?: return null
        return try {
            val network = manager.activeNetwork ?: return null
            val capabilities = manager.getNetworkCapabilities(network) ?: return null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
            if (!capabilities.matches(connectionType)) return null
            val actualInterface = manager.getLinkProperties(network)?.interfaceName
            if (!interfaceName.isNullOrBlank() && actualInterface != interfaceName) return null
            network
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun NetworkCapabilities.matches(connectionType: ConnectionType): Boolean = when (connectionType) {
        ConnectionType.WIFI -> hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ConnectionType.ETHERNET -> hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        else -> false
    }
}
