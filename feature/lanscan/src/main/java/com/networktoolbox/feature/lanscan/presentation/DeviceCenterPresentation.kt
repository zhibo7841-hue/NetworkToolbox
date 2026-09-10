package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange

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

    /** Returns a real aggregated name, or the neutral fallback required by Devices. */
    fun deviceDisplayName(device: LanDevice): String =
        LanScannerPresentation.deviceDisplayName(device)

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
