package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanScanRangeSource
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus

object LanScanHistorySerializer {
    fun toHistoryRecord(session: LanScanSession): HistoryRecord {
        require(session.status == LanScanStatus.COMPLETED) {
            "Only completed LAN scans can be saved to history."
        }
        val range = requireNotNull(session.range)
        val summary = "${range.displayLabel} · 发现 ${session.discoveredDevices.size} 台设备"
        return HistoryRecord(
            timestamp = session.finishedAt,
            type = HistoryType.LAN_SCAN,
            title = "局域网扫描",
            summary = summary,
            detailJson = buildJson(session, summary),
        )
    }

    private fun buildJson(session: LanScanSession, summary: String): String {
        val range = requireNotNull(session.range)
        return jsonObject(
            "schemaVersion" to "1",
            "status" to jsonString(session.status.name),
            "range" to jsonString(range.displayLabel),
            "rangeSource" to jsonString(range.rangeSource.name),
            "originalRange" to jsonString(
                if (range.rangeSource == LanScanRangeSource.CUSTOM) {
                    range.displayLabel
                } else {
                    range.originalCidr
                },
            ),
            "rangeWasLimited" to session.rangeWasLimited.toString(),
            "scannedHosts" to session.scannedHosts.toString(),
            "totalHosts" to session.totalHosts.toString(),
            "discoveredCount" to session.discoveredDevices.size.toString(),
            "durationMs" to session.elapsedMs.toString(),
            "summary" to jsonString(summary),
            "devices" to session.discoveredDevices.joinToString(
                prefix = "[",
                postfix = "]",
            ) { device -> deviceJson(device) },
        )
    }

    private fun deviceJson(device: LanDevice): String = jsonObject(
        "ipAddress" to jsonString(device.ipAddress),
        "isLocalDevice" to device.isLocalDevice.toString(),
        "isGateway" to device.isGateway.toString(),
        "latencyMs" to (device.latencyMs?.toString() ?: "null"),
        "discoveryMethods" to device.discoveryMethods.joinToString(
            prefix = "[",
            postfix = "]",
        ) { method -> jsonString(method.name) },
    )

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:$value"
        }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}
