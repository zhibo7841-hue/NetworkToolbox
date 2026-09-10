package com.networktoolbox.core.common.favorites

/** The strength-ordered identity sources used for a saved LAN device. */
enum class FavoriteIdentityType {
    MAC,
    PROTOCOL,
    NETWORK_IP,
}

data class FavoriteDeviceIdentity(
    val type: FavoriteIdentityType,
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Favorite identity value must not be blank." }
    }
}

/** A scan observation reduced to the fields needed for conservative matching. */
data class FavoriteDeviceCandidate(
    val networkScope: String,
    val ipv4Address: String,
    val macAddress: String? = null,
    val protocolIdentity: String? = null,
) {
    init {
        require(networkScope.isNotBlank()) { "Network scope must not be blank." }
        require(FavoriteIdentityMatcher.normalizeIpv4(ipv4Address) != null) {
            "Favorite candidate must contain a valid IPv4 address."
        }
    }

    val identity: FavoriteDeviceIdentity
        get() = FavoriteIdentityMatcher.identityFor(this)
}

data class FavoriteDeviceObservation(
    val lastKnownIpv4: String,
    val lastKnownDisplayName: String?,
    val lastKnownHostname: String?,
    val lastKnownMdnsName: String?,
    val lastKnownUpnpName: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val lastSeenAt: Long,
    val isGateway: Boolean,
    val isLocalDevice: Boolean,
)

data class FavoriteDevice(
    val id: Long = 0L,
    val identityType: FavoriteIdentityType,
    val identityValue: String,
    val networkScope: String,
    val lastKnownIpv4: String?,
    val lastKnownDisplayName: String?,
    val lastKnownHostname: String?,
    val lastKnownMdnsName: String?,
    val lastKnownUpnpName: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val createdAt: Long,
    val lastSeenAt: Long?,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false,
) {
    init {
        require(identityValue.isNotBlank()) { "Favorite identity value must not be blank." }
        require(networkScope.isNotBlank()) { "Network scope must not be blank." }
    }

    val identity: FavoriteDeviceIdentity
        get() = FavoriteDeviceIdentity(identityType, identityValue)
}
