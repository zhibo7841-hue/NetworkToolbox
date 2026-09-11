package com.networktoolbox.core.network.wol

sealed interface WakeOnLanTransportResult {
    data object Sent : WakeOnLanTransportResult

    data object PermissionDenied : WakeOnLanTransportResult

    data object Failed : WakeOnLanTransportResult
}

interface WakeOnLanSender {
    suspend fun send(
        binding: LanNetworkBinding,
        destinationAddress: String,
        udpPort: Int,
        payload: ByteArray,
    ): WakeOnLanTransportResult
}
