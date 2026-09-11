package com.networktoolbox.core.common.wol

sealed interface WakeOnLanResult {
    data class Sent(
        val destinationAddress: String,
        val udpPort: Int,
    ) : WakeOnLanResult

    data class Failed(
        val reason: WakeOnLanFailureReason,
    ) : WakeOnLanResult

    data object Cancelled : WakeOnLanResult
}

enum class WakeOnLanFailureReason {
    NOT_CONFIGURED,
    INVALID_CONFIG,
    NO_ACTIVE_LAN,
    UNSUPPORTED_NETWORK,
    NO_IPV4,
    BROADCAST_UNAVAILABLE,
    NETWORK_SCOPE_MISMATCH,
    PERMISSION_DENIED,
    SEND_FAILED,
}
