package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext

internal data class NetworkContextSnapshot(
    val connectionType: ConnectionType = ConnectionType.UNKNOWN,
    val ipv4Address: String? = null,
    val ipv6Address: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val vpnActive: Boolean? = null,
    val wifiName: String? = null,
    val wifiSignalLevel: Int? = null,
)

internal object NetworkContextMapper {
    fun map(snapshot: NetworkContextSnapshot): NetworkContext = NetworkContext(
        connectionType = snapshot.connectionType,
        ipv4Address = snapshot.ipv4Address,
        ipv6Address = snapshot.ipv6Address,
        gateway = snapshot.gateway,
        dnsServers = snapshot.dnsServers,
        vpnActive = snapshot.vpnActive,
        wifiName = snapshot.wifiName,
        wifiSignalLevel = snapshot.wifiSignalLevel,
    )
}
