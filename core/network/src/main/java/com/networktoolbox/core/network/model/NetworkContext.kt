package com.networktoolbox.core.network.model

data class NetworkContext(
    val connectionType: ConnectionType,
    val ipv4Address: String?,
    val ipv6Address: String?,
    val gateway: String?,
    val dnsServers: List<String>,
    val vpnActive: Boolean?,
    val wifiName: String?,
    val wifiSignalLevel: Int?,
    /** Whether the platform explicitly exposed an active default network. */
    val activeNetworkAvailable: Boolean? = null,
    /** Whether Android reported that the active network was validated for Internet access. */
    val validated: Boolean? = null,
) {
    companion object {
        fun unknown(): NetworkContext = NetworkContext(
            connectionType = ConnectionType.UNKNOWN,
            ipv4Address = null,
            ipv6Address = null,
            gateway = null,
            dnsServers = emptyList(),
            vpnActive = null,
            wifiName = null,
            wifiSignalLevel = null,
            activeNetworkAvailable = null,
            validated = null,
        )

        fun noActiveNetwork(): NetworkContext = NetworkContext(
            connectionType = ConnectionType.UNKNOWN,
            ipv4Address = null,
            ipv6Address = null,
            gateway = null,
            dnsServers = emptyList(),
            vpnActive = null,
            wifiName = null,
            wifiSignalLevel = null,
            activeNetworkAvailable = false,
            validated = false,
        )
    }
}
