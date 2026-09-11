package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.LanFavoriteIdentity
import com.networktoolbox.feature.lanscan.domain.LanNetworkScope
import com.networktoolbox.feature.lanscan.domain.model.identity
import com.networktoolbox.core.common.ipv4.IPv4Address

/**
 * Small presentation values for the top-level Devices destination.
 *
 * The Device Center deliberately reuses [LanScannerPresentation] and the
 * existing identity aggregator. It does not create a second device model or
 * infer device metadata that the scanner did not observe.
 */
data class DeviceCenterNetworkSummary(
    val networkLabel: String,
    val networkName: String?,
    val subnet: String?,
    val localAddress: String?,
    val gateway: String?,
    val wifiSignalLevel: Int?,
)

data class LanDeviceCardPresentation(
    val displayName: String,
    val ipAddress: String,
    val identitySummary: String? = null,
    val evidence: String? = null,
    val role: String? = null,
    val macAddress: String? = null,
    val isFavorite: Boolean = false,
)

data class DeviceCenterDeviceItem(
    val device: LanDevice? = null,
    val favorite: FavoriteDevice? = null,
    val observedThisScan: Boolean,
    val isFavorite: Boolean,
    val detailKey: String,
    val card: LanDeviceCardPresentation,
)

data class DeviceDetailPresentation(
    val detailKey: String,
    val displayName: String,
    val ipAddress: String?,
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    val hostname: String?,
    val mdnsNames: List<String>,
    val upnpNames: List<String>,
    val role: String?,
    /** User-facing label only; opaque scope fingerprints are never displayed. */
    val networkScope: String?,
    val observedThisScan: Boolean,
    val lastSeenAt: Long?,
    val customName: String? = null,
    val isFavorite: Boolean,
    val canToggleFavorite: Boolean,
) {
    /** Derived labels keep the star, status text, and accessibility semantics aligned. */
    val favoriteStatusLabel: String
        get() = if (isFavorite) "已收藏" else "未收藏"

    val favoriteToggleContentDescription: String
        get() = if (isFavorite) "取消收藏" else "收藏设备"
}

object DeviceCenterPresentation {
    fun networkSummary(
        context: NetworkContext,
        range: LanScanRange? = null,
    ): DeviceCenterNetworkSummary = DeviceCenterNetworkSummary(
        networkLabel = context.connectionType.displayName(),
        networkName = context.wifiName
            ?.trim()
            ?.takeIf { context.connectionType == ConnectionType.WIFI && it.isRealWifiName() },
        subnet = range?.displayLabel,
        localAddress = context.ipv4Address?.trim()?.takeIf(String::isNotBlank),
        gateway = context.gateway
            ?.trim()
            ?.takeIf { it.isNotBlank() && context.connectionType.isLocalNetwork() },
        wifiSignalLevel = context.wifiSignalLevel
            ?.takeIf { context.connectionType == ConnectionType.WIFI },
    )

    /** Resolves custom override before the detected identity and neutral fallback. */
    fun deviceDisplayName(device: LanDevice): String = deviceDisplayName(device, null)

    fun deviceDisplayName(device: LanDevice, favorite: FavoriteDevice?): String =
        DeviceDisplayNameResolver.resolve(
            customName = favorite?.customName,
            detectedName = LanScannerPresentation.devicePrimaryText(device)
                .takeUnless { it == device.ipAddress },
        )

    fun deviceAddress(device: LanDevice): String = device.ipAddress

    fun deviceIdentitySummary(device: LanDevice): String? =
        LanScannerPresentation.deviceIdentitySummary(device)

    fun deviceRole(device: LanDevice): String = LanScannerPresentation.deviceRole(device)

    /**
     * The evidence line stays grounded in the scanner's confirmed evidence.
     * Ordinary devices never receive a synthetic "在线" badge.
     */
    fun deviceEvidence(device: LanDevice): String? =
        LanScannerPresentation.deviceSecondaryText(device)

    /**
     * Merges current scan observations with saved favorites without changing
     * the scanner's ordering or turning an absent observation into offline.
     */
    fun deviceList(
        devices: List<LanDevice>,
        favorites: List<FavoriteDevice>,
        context: NetworkContext,
        includeUnseenFavorites: Boolean = true,
        unseenEvidence: String = "本次未发现",
    ): List<DeviceCenterDeviceItem> {
        val scope = LanNetworkScope.from(context)
        val scopedFavorites = favorites.filter { it.networkScope == scope }
        val observedItems = devices.mapNotNull { device ->
            val candidate = LanFavoriteIdentity.candidate(device, context)
            val favorite = candidate?.let { current ->
                scopedFavorites.firstOrNull { saved ->
                    FavoriteIdentityMatcher.matches(saved, current)
                }
            }
            val detailKey = favorite
                ?.takeIf { it.isFavorite }
                ?.let(LanDeviceDetailRouteKey::forFavorite)
                ?: LanDeviceDetailRouteKey.forObserved(scope, device.ipAddress)
            DeviceCenterDeviceItem(
                device = device,
                favorite = favorite,
                observedThisScan = true,
                isFavorite = favorite?.isFavorite == true,
                detailKey = detailKey,
                card = card(device, favorite),
            )
        }
        val observedFavoriteKeys = observedItems.mapNotNull { it.favorite?.let(::favoriteKey) }.toSet()
        val unseenItems = if (includeUnseenFavorites) {
            scopedFavorites
                .filter { it.isFavorite || it.customName != null }
                .filterNot { favorite -> favoriteKey(favorite) in observedFavoriteKeys }
                .map { favorite ->
                    DeviceCenterDeviceItem(
                        favorite = favorite,
                        observedThisScan = false,
                        isFavorite = favorite.isFavorite,
                        detailKey = LanDeviceDetailRouteKey.forFavorite(favorite),
                        card = card(favorite, unseenEvidence),
                    )
                }
        } else {
            emptyList()
        }

        return (observedItems + unseenItems).sortedWith(
            compareBy<DeviceCenterDeviceItem>(
                { item -> itemGroup(item) },
                { item -> ipv4SortValue(item.card.ipAddress) },
                { item -> item.card.displayName },
            ),
        )
    }

    /**
     * Saved profiles are deliberately presented separately before the first
     * scan of a scope. The neutral text describes the session state and never
     * implies that the device is offline.
     */
    fun savedProfilesBeforeScan(
        favorites: List<FavoriteDevice>,
        context: NetworkContext,
        unseenEvidence: String = "尚未进行本次扫描",
    ): List<DeviceCenterDeviceItem> = deviceList(
        devices = emptyList(),
        favorites = favorites,
        context = context,
        includeUnseenFavorites = true,
        unseenEvidence = unseenEvidence,
    )

    fun detail(
        device: LanDevice,
        favorite: FavoriteDevice?,
        context: NetworkContext,
        observedThisScan: Boolean = true,
        detailKey: String = LanDeviceDetailRouteKey.forObserved(
            LanNetworkScope.from(context),
            device.ipAddress,
        ),
    ): DeviceDetailPresentation {
        val identity = device.identity
        val role = deviceRole(device).takeIf(String::isNotBlank)
            ?: favorite?.let(::favoriteRole)
        val mdnsNames = device.mdnsObservations
            .flatMap { observation -> listOf(observation.serviceName, observation.hostname.orEmpty()) }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOfNotNull(favorite?.lastKnownMdnsName) }
        val upnpNames = device.upnpObservations
            .mapNotNull { it.friendlyName?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .ifEmpty { listOfNotNull(favorite?.lastKnownUpnpName) }
        return DeviceDetailPresentation(
            detailKey = detailKey,
            displayName = deviceDisplayName(device, favorite),
            ipAddress = device.ipAddress,
            macAddress = FavoriteIdentityMatcher.normalizeMac(device.macAddress)
                ?: favorite?.macAddress,
            vendor = identity.manufacturer?.value ?: favorite?.vendor,
            model = identity.modelName?.value
                ?: identity.modelDescription?.value
                ?: identity.modelNumber?.value
                ?: favorite?.model,
            hostname = identity.hostname?.value ?: favorite?.lastKnownHostname,
            mdnsNames = mdnsNames,
            upnpNames = upnpNames,
            role = role,
            networkScope = LanNetworkScope.from(context)?.let { "当前局域网" },
            observedThisScan = observedThisScan,
            lastSeenAt = device.lastSeen,
            customName = favorite?.customName,
            isFavorite = favorite?.isFavorite == true,
            canToggleFavorite = LanNetworkScope.from(context) != null,
        )
    }

    fun detail(
        favorite: FavoriteDevice,
        context: NetworkContext,
        detailKey: String = LanDeviceDetailRouteKey.forFavorite(favorite),
    ): DeviceDetailPresentation = DeviceDetailPresentation(
        detailKey = detailKey,
        displayName = DeviceDisplayNameResolver.resolve(
            customName = favorite.customName,
            detectedName = favorite.lastKnownDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != favorite.lastKnownIpv4 },
        ),
        ipAddress = favorite.lastKnownIpv4,
        macAddress = FavoriteIdentityMatcher.normalizeMac(favorite.macAddress),
        vendor = favorite.vendor,
        model = favorite.model,
        hostname = favorite.lastKnownHostname,
        mdnsNames = listOfNotNull(favorite.lastKnownMdnsName),
        upnpNames = listOfNotNull(favorite.lastKnownUpnpName),
        role = favoriteRole(favorite),
        networkScope = LanNetworkScope.from(context)?.let { "当前局域网" },
        observedThisScan = false,
        lastSeenAt = favorite.lastSeenAt,
        customName = favorite.customName,
        isFavorite = favorite.isFavorite,
        canToggleFavorite = LanNetworkScope.from(context) != null,
    )

    private fun card(device: LanDevice, favorite: FavoriteDevice?): LanDeviceCardPresentation =
        LanDeviceCardPresentation(
            displayName = deviceDisplayName(device, favorite),
            ipAddress = device.ipAddress,
            identitySummary = deviceIdentitySummary(device),
            evidence = deviceEvidence(device),
            role = deviceRole(device).takeIf(String::isNotBlank),
            macAddress = device.macAddress,
            isFavorite = favorite?.isFavorite == true,
        )

    private fun card(
        favorite: FavoriteDevice,
        evidence: String = "本次未发现",
    ): LanDeviceCardPresentation =
        LanDeviceCardPresentation(
            displayName = DeviceDisplayNameResolver.resolve(
                customName = favorite.customName,
                detectedName = favorite.lastKnownDisplayName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it != favorite.lastKnownIpv4 },
            ),
            ipAddress = favorite.lastKnownIpv4 ?: "地址未知",
            identitySummary = listOfNotNull(favorite.vendor, favorite.model)
                .joinToString(" · ")
                .takeIf(String::isNotBlank),
            evidence = evidence,
            role = favoriteRole(favorite).takeIf(String::isNotBlank),
            macAddress = favorite.macAddress,
            isFavorite = favorite.isFavorite,
        )

    private fun itemGroup(item: DeviceCenterDeviceItem): Int = when {
        item.observedThisScan && item.isFavorite -> 0
        item.observedThisScan && (item.device?.isGateway == true || item.device?.isLocalDevice == true) -> 1
        item.observedThisScan -> 2
        else -> 3
    }

    private fun favoriteKey(favorite: FavoriteDevice): String = listOf(
        favorite.networkScope,
        favorite.identityType.name,
        favorite.identityValue,
    ).joinToString("\u0000")

    private fun favoriteRole(favorite: FavoriteDevice): String = buildList {
        if (favorite.isLocalDevice) add("本机")
        if (favorite.isGateway) add("网关")
    }.joinToString(" · ")

    private fun ipv4SortValue(value: String): Long = IPv4Address.parse(value)?.value ?: Long.MAX_VALUE

    private fun ConnectionType.isLocalNetwork(): Boolean = this == ConnectionType.WIFI ||
        this == ConnectionType.ETHERNET

    private fun ConnectionType.displayName(): String = when (this) {
        ConnectionType.WIFI -> "Wi-Fi"
        ConnectionType.ETHERNET -> "以太网"
        ConnectionType.CELLULAR -> "移动网络"
        ConnectionType.VPN -> "VPN"
        ConnectionType.BLUETOOTH -> "蓝牙"
        ConnectionType.UNKNOWN -> "未知网络"
    }

    private fun String.isRealWifiName(): Boolean = lowercase() !in setOf(
        "<unknown ssid>",
        "unknown ssid",
        "unknown",
    )
}
