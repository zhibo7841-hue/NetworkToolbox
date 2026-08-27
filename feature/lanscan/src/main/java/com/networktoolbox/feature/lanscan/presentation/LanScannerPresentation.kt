package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import java.util.Locale

object LanScannerPresentation {
    fun deviceRole(device: LanDevice): String = buildList {
        if (device.isLocalDevice) add("本机")
        if (device.isGateway) add("网关")
        if (isEmpty()) add("在线")
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

    fun elapsedText(elapsedMs: Long): String = when {
        elapsedMs < 1_000L -> "$elapsedMs 毫秒"
        else -> String.format(Locale.US, "%.1f 秒", elapsedMs / 1_000.0)
    }

    fun progressFraction(scannedHosts: Int, totalHosts: Int): Float =
        if (totalHosts <= 0) 0f else (scannedHosts.toFloat() / totalHosts).coerceIn(0f, 1f)

    private fun evidenceLabel(evidence: LanDeviceEvidence): String? = when (evidence.method) {
        LanDiscoveryMethod.REACHABILITY -> "可达性检测"
        LanDiscoveryMethod.TCP -> evidence.successfulPort?.let { "TCP $it 响应" } ?: "TCP 响应"
        LanDiscoveryMethod.LOCAL_CONTEXT -> "本机"
        LanDiscoveryMethod.GATEWAY_CONTEXT -> "网关"
    }
}
