package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.StatusVisualState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HistoryCardContent(
    val title: String,
    val secondaryTitle: String?,
    val summary: String,
    val metadata: String?,
)

/**
 * Maps stored, structured history payloads to compact UI semantics.
 *
 * The mapper deliberately never infers a diagnostic status from a free-form
 * summary. Older records without a structured status therefore remain
 * UNKNOWN instead of being presented as healthy or failed by guesswork.
 */
internal data class HistoryStatusVisual(
    val state: StatusVisualState,
    val label: String,
)

internal object HistoryRecordPresentation {
    fun cardContent(
        typeTitle: String,
        titleCandidate: String?,
        summary: String,
        metadata: List<String>,
    ): HistoryCardContent = HistoryCardContent(
        title = typeTitle,
        secondaryTitle = titleCandidate
            ?.takeIf { it.isNotBlank() && it != typeTitle },
        summary = summary,
        metadata = metadata
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotBlank),
    )

    fun timeLabel(
        timestamp: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): String {
        val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
        val time = DateTimeFormatter.ofPattern("HH:mm").format(dateTime)
        return when (dateTime.toLocalDate()) {
            today -> "今天 $time"
            today.minusDays(1) -> "昨天 $time"
            else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dateTime)
        }
    }

    fun status(record: HistoryRecord): HistoryStatusVisual = when (record.type) {
        HistoryType.REPORT -> reportStatus(record.detailJson)
        HistoryType.PING -> record.detailJson.readJsonBoolean("success")?.let { success ->
            if (success) normal() else warning("异常")
        } ?: unknown()

        HistoryType.DNS -> dnsStatus(record.detailJson)
        HistoryType.TCP -> record.detailJson.readJsonBoolean("success")?.let { success ->
            if (success) normal() else warning("异常")
        } ?: unknown()

        // A completed LAN scan is itself a successful local operation. There
        // is no network fault status in the LAN scan history contract.
        HistoryType.LAN_SCAN -> normal()
        HistoryType.UNKNOWN -> unknown()
    }

    fun networkLabel(record: HistoryRecord): String? {
        if (record.type != HistoryType.REPORT) return null
        val raw = record.detailJson.readNestedJsonString("networkSummary", "connectionType")
            ?: record.detailJson.readNestedJsonString("networkSnapshot", "connectionType")
            ?: record.detailJson.readNestedJsonString("evidence", "connectionType")
        return when (raw?.uppercase()) {
            "WIFI" -> "Wi-Fi"
            "CELLULAR" -> "移动网络"
            "ETHERNET" -> "以太网"
            "VPN" -> "VPN"
            "BLUETOOTH" -> "蓝牙"
            else -> null
        }
    }

    private fun reportStatus(json: String): HistoryStatusVisual = statusVisual(
        json.readNestedJsonString("diagnosis", "status")
            ?: json.readJsonString("overallStatus"),
    )

    private fun dnsStatus(json: String): HistoryStatusVisual {
        val status = json.readJsonString("status")?.uppercase() ?: return unknown()
        return when (status) {
            "SUCCESS" -> normal()
            "NO_RECORDS" -> notice("无记录")
            "NXDOMAIN" -> warning("域名不存在")
            "PARTIAL" -> warning("部分完成")
            "TIMEOUT", "NETWORK_ERROR", "INVALID_RESPONSE", "FAILED", "INVALID_QUERY" ->
                error("严重异常")

            else -> unknown()
        }
    }

    private fun statusVisual(raw: String?): HistoryStatusVisual = when (raw?.uppercase()) {
        "NORMAL", "HEALTHY" -> normal()
        "ATTENTION", "NOTICE" -> notice("需要关注")
        "LIMITED", "WARNING" -> warning("异常")
        "ERROR" -> error("严重异常")
        "UNKNOWN" -> unknown()
        else -> unknown()
    }

    private fun normal() = HistoryStatusVisual(StatusVisualState.NORMAL, "正常")

    private fun notice(label: String) = HistoryStatusVisual(StatusVisualState.NOTICE, label)

    private fun warning(label: String) = HistoryStatusVisual(StatusVisualState.WARNING, label)

    private fun error(label: String) = HistoryStatusVisual(StatusVisualState.ERROR, label)

    private fun unknown() = HistoryStatusVisual(StatusVisualState.UNKNOWN, "未确定")
}

private fun String.readJsonBoolean(key: String): Boolean? {
    val marker = "\"$key\":"
    val valueStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    return when {
        startsWith("true", valueStart) -> true
        startsWith("false", valueStart) -> false
        else -> null
    }
}

private fun String.readJsonString(key: String): String? {
    val marker = "\"$key\":\""
    val valueStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    return readJsonStringAt(valueStart)
}

private fun String.readNestedJsonString(parentKey: String, key: String): String? {
    val marker = "\"$parentKey\":{"
    val objectStart = indexOf(marker).takeIf { it >= 0 }?.plus(marker.length - 1) ?: return null
    val objectEnd = matchingObjectEnd(objectStart) ?: return null
    return substring(objectStart, objectEnd + 1).readJsonString(key)
}

private fun String.readJsonStringAt(start: Int): String? {
    val value = StringBuilder()
    var index = start
    while (index < length) {
        when (val character = this[index]) {
            '"' -> return value.toString()
            '\\' -> {
                if (index + 1 >= length) return null
                val escaped = this[index + 1]
                value.append(
                    when (escaped) {
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> escaped
                    },
                )
                index += 2
            }

            else -> {
                value.append(character)
                index++
            }
        }
    }
    return null
}

private fun String.matchingObjectEnd(start: Int): Int? {
    if (start !in indices || this[start] != '{') return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until length) {
        val character = this[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                inString = false
            }
            continue
        }
        when (character) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return null
}
