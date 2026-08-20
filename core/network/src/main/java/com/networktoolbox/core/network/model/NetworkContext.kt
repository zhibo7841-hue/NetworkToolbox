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
        )
    }
}
