package com.networktoolbox.core.common.wol

data class WakeOnLanConfig(
    val macAddress: MacAddress,
    val udpPort: Int = DEFAULT_UDP_PORT,
) {
    init {
        require(udpPort in MIN_UDP_PORT..MAX_UDP_PORT) {
            "Wake-on-LAN UDP port must be between $MIN_UDP_PORT and $MAX_UDP_PORT."
        }
    }

    companion object {
        const val DEFAULT_UDP_PORT = 9
        const val MIN_UDP_PORT = 1
        const val MAX_UDP_PORT = 65535
    }
}
