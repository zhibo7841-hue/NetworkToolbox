package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import java.util.Base64

/** A stable, small navigation key; full device observations never enter app navigation state. */
object LanDeviceDetailRouteKey {
    sealed interface Parsed {
        data class Observed(val networkScope: String?, val ipv4Address: String) : Parsed

        data class Favorite(
            val networkScope: String,
            val identityType: FavoriteIdentityType,
            val identityValue: String,
        ) : Parsed
    }

    fun forObserved(networkScope: String?, ipv4Address: String): String =
        "observed:${encode(networkScope ?: UNSCOPED)}:${encode(
            FavoriteIdentityMatcher.normalizeIpv4(ipv4Address) ?: ipv4Address.trim(),
        )}"

    fun forFavorite(favorite: FavoriteDevice): String =
        "favorite:${encode(favorite.networkScope)}:${encode(favorite.identityType.name)}:${encode(favorite.identityValue)}"

    fun parse(value: String?): Parsed? {
        val parts = value?.split(':') ?: return null
        return when (parts.firstOrNull()) {
            "observed" -> if (parts.size == 3) {
                val scope = decode(parts[1]) ?: return null
                val ip = decode(parts[2])?.let(FavoriteIdentityMatcher::normalizeIpv4) ?: return null
                Parsed.Observed(scope.takeUnless { it == UNSCOPED }, ip)
            } else {
                null
            }

            "favorite" -> if (parts.size == 4) {
                val scope = decode(parts[1])?.takeIf(String::isNotBlank) ?: return null
                val type = decode(parts[2])?.let {
                    runCatching { FavoriteIdentityType.valueOf(it) }.getOrNull()
                } ?: return null
                val identity = decode(parts[3])?.takeIf(String::isNotBlank) ?: return null
                Parsed.Favorite(scope, type, identity)
            } else {
                null
            }

            else -> null
        }
    }

    private fun encode(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

    private const val UNSCOPED = "unscoped"
}
