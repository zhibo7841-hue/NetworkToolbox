package com.networktoolbox.core.common.favorites

import com.networktoolbox.core.common.ipv4.IPv4Address
import java.util.Locale

/**
 * Conservative identity matching for saved LAN devices.
 *
 * A stronger identity never falls back to a weaker one. This intentionally
 * prefers missing a match over merging two different devices.
 */
object FavoriteIdentityMatcher {
    fun normalizeMac(value: String?): String? {
        val compact = value
            ?.trim()
            ?.replace(":", "")
            ?.replace("-", "")
            ?.replace(".", "")
            ?.takeIf {
                it.length == 12 && it.all { character ->
                    character in '0'..'9' || character.lowercaseChar() in 'a'..'f'
                }
            }
            ?.uppercase(Locale.ROOT)
            ?: return null

        if (compact == "000000000000" || compact == "FFFFFFFFFFFF" || compact == ANDROID_PLACEHOLDER) {
            return null
        }
        return compact.chunked(2).joinToString(":")
    }

    fun normalizeProtocol(value: String?): String? = value
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.ROOT)

    fun normalizeIpv4(value: String?): String? = value
        ?.let(IPv4Address::parse)
        ?.toDottedDecimal()

    fun identityFor(candidate: FavoriteDeviceCandidate): FavoriteDeviceIdentity {
        normalizeMac(candidate.macAddress)?.let { return FavoriteDeviceIdentity(FavoriteIdentityType.MAC, it) }
        normalizeProtocol(candidate.protocolIdentity)?.let {
            return FavoriteDeviceIdentity(FavoriteIdentityType.PROTOCOL, it)
        }
        return FavoriteDeviceIdentity(
            FavoriteIdentityType.NETWORK_IP,
            normalizeIpv4(candidate.ipv4Address)
                ?: error("Favorite candidate must contain a valid IPv4 address."),
        )
    }

    fun matches(saved: FavoriteDevice, candidate: FavoriteDeviceCandidate): Boolean {
        if (saved.networkScope != candidate.networkScope) return false

        val candidateMac = normalizeMac(candidate.macAddress)
        val savedMac = normalizeMac(saved.macAddress) ?:
            normalizeMac(saved.identityValue.takeIf { saved.identityType == FavoriteIdentityType.MAC })
        if (saved.identityType == FavoriteIdentityType.MAC || candidateMac != null) {
            return saved.identityType == FavoriteIdentityType.MAC &&
                savedMac != null && candidateMac != null && savedMac == candidateMac
        }

        val candidateProtocol = normalizeProtocol(candidate.protocolIdentity)
        if (saved.identityType == FavoriteIdentityType.PROTOCOL || candidateProtocol != null) {
            return saved.identityType == FavoriteIdentityType.PROTOCOL &&
                normalizeProtocol(saved.identityValue) != null &&
                normalizeProtocol(saved.identityValue) == candidateProtocol
        }

        return saved.identityType == FavoriteIdentityType.NETWORK_IP &&
            normalizeIpv4(saved.lastKnownIpv4) == normalizeIpv4(candidate.ipv4Address)
    }

    fun canonicalIdentity(identity: FavoriteDeviceIdentity): FavoriteDeviceIdentity = when (identity.type) {
        FavoriteIdentityType.MAC -> FavoriteDeviceIdentity(
            FavoriteIdentityType.MAC,
            normalizeMac(identity.value) ?: identity.value.trim().uppercase(Locale.ROOT),
        )

        FavoriteIdentityType.PROTOCOL -> FavoriteDeviceIdentity(
            FavoriteIdentityType.PROTOCOL,
            normalizeProtocol(identity.value) ?: identity.value.trim().lowercase(Locale.ROOT),
        )

        FavoriteIdentityType.NETWORK_IP -> FavoriteDeviceIdentity(
            FavoriteIdentityType.NETWORK_IP,
            normalizeIpv4(identity.value) ?: identity.value.trim(),
        )
    }

    private const val ANDROID_PLACEHOLDER = "020000000000"
}
