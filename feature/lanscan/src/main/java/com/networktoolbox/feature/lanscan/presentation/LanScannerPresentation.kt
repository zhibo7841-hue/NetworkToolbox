package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import java.util.Locale

object LanScannerPresentation {
    fun devicePrimaryText(device: LanDevice): String =
        device.hostName?.takeIf(String::isNotBlank)
            ?: device.mdnsDisplayNameCandidate?.takeIf(String::isNotBlank)?.let { "mDNS：$it" }
            ?: device.ipAddress

    fun deviceAddressText(device: LanDevice): String? = device.ipAddress.takeIf {
        devicePrimaryText(device) != device.ipAddress
    }

    fun deviceRole(device: LanDevice): String = buildList {
        if (device.isLocalDevice) add("本机")
        if (device.isGateway) add("网关")
    }.joinToString(" · ")

    fun discoveryEvidence(device: LanDevice): String? {
        val evidence = device.discoveryEvidence.ifEmpty {
            device.discoveryMethods.map(::LanDeviceEvidence)
        }
        return evidence.mapNotNull(::evidenceLabel)
            .distinct()
            .joinToString(" · ")
            .takeIf(String::isNotBlank)
    }

    fun deviceSecondaryText(device: LanDevice): String? {
        if (device.isLocalDevice) return "当前设备"
        if (device.isGateway) return "网关信息"

        return listOfNotNull(
            discoveryEvidence(device),
            device.latencyMs?.let { "$it ms" },
        ).joinToString(" · ").takeIf(String::isNotBlank)
    }

    fun sessionSummary(session: LanScanSession): String =
        "${session.totalHosts} 个地址 · ${session.discoveredDevices.size} 台设备 · ${elapsedText(session.elapsedMs)}"

    fun elapsedText(elapsedMs: Long): String = when {
        elapsedMs < 1_000L -> "$elapsedMs 毫秒"
        else -> String.format(Locale.US, "%.1f 秒", elapsedMs / 1_000.0)
    }

    fun progressFraction(scannedHosts: Int, totalHosts: Int): Float =
        if (totalHosts <= 0) 0f else (scannedHosts.toFloat() / totalHosts).coerceIn(0f, 1f)

    private fun evidenceLabel(evidence: LanDeviceEvidence): String? = when (evidence.method) {
        LanDiscoveryMethod.REACHABILITY -> "可达性检测"
        LanDiscoveryMethod.TCP -> evidence.successfulPort?.let { "TCP $it 可连接" } ?: "TCP 可连接"
        LanDiscoveryMethod.LOCAL_CONTEXT -> "本机"
        LanDiscoveryMethod.GATEWAY_CONTEXT -> "网关"
    }
}
