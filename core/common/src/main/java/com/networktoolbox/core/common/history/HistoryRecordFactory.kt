package com.networktoolbox.core.common.history

object HistoryRecordFactory {
    fun ping(
        timestamp: Long,
        target: String,
        success: Boolean,
        latencyMs: Long?,
        method: String,
        errorMessage: String?,
    ): HistoryRecord = HistoryRecord(
        timestamp = timestamp,
        type = HistoryType.PING,
        title = "Ping · $target",
        summary = if (success) "Ping completed" else "Ping failed",
        detailJson = jsonObject(
            "target" to jsonString(target),
            "success" to success.toString(),
            "latencyMs" to jsonNumber(latencyMs),
            "method" to jsonString(method),
            "errorMessage" to jsonNullableString(errorMessage),
        ),
    )

    fun pingSession(
        timestamp: Long,
        target: String,
        address: String?,
        protocol: String,
        mode: String,
        startTime: Long,
        endTime: Long,
        sentPackets: Int,
        receivedPackets: Int,
        lostPackets: Int,
        packetLoss: Double,
        minLatencyMs: Long?,
        avgLatencyMs: Double?,
        maxLatencyMs: Long?,
        jitterMs: Double?,
        qualityLevel: String,
        method: String,
        summary: String,
        errorMessage: String?,
    ): HistoryRecord = HistoryRecord(
        timestamp = timestamp,
        type = HistoryType.PING,
        title = "Ping · $target",
        summary = summary,
        detailJson = jsonObject(
            "target" to jsonString(target),
            "address" to jsonNullableString(address),
            "protocol" to jsonString(protocol),
            "mode" to jsonString(mode),
            "startTime" to startTime.toString(),
            "endTime" to endTime.toString(),
            "sentPackets" to sentPackets.toString(),
            "receivedPackets" to receivedPackets.toString(),
            "lostPackets" to lostPackets.toString(),
            "packetLoss" to packetLoss.toString(),
            "minLatencyMs" to jsonNumber(minLatencyMs),
            "avgLatencyMs" to jsonDouble(avgLatencyMs),
            "maxLatencyMs" to jsonNumber(maxLatencyMs),
            "jitterMs" to jsonDouble(jitterMs),
            "qualityLevel" to jsonString(qualityLevel),
            "method" to jsonString(method),
            "summary" to jsonString(summary),
            "errorMessage" to jsonNullableString(errorMessage),
        ),
    )

    fun dns(
        timestamp: Long,
        domain: String,
        success: Boolean,
        aRecords: List<String>,
        aaaaRecords: List<String>,
        durationMs: Long?,
    ): HistoryRecord = HistoryRecord(
        timestamp = timestamp,
        type = HistoryType.DNS,
        title = "DNS · $domain",
        summary = if (success) "DNS lookup completed" else "DNS lookup failed",
        detailJson = jsonObject(
            "domain" to jsonString(domain),
            "success" to success.toString(),
            "aRecords" to jsonStringArray(aRecords),
            "aaaaRecords" to jsonStringArray(aaaaRecords),
            "durationMs" to jsonNumber(durationMs),
        ),
    )

    fun tcp(
        timestamp: Long,
        host: String,
        port: Int,
        success: Boolean,
        latencyMs: Long?,
        errorMessage: String?,
    ): HistoryRecord = HistoryRecord(
        timestamp = timestamp,
        type = HistoryType.TCP,
        title = "TCP · $host:$port",
        summary = if (success) "TCP port check completed" else "TCP port check failed",
        detailJson = jsonObject(
            "host" to jsonString(host),
            "port" to port.toString(),
            "success" to success.toString(),
            "latencyMs" to jsonNumber(latencyMs),
            "errorMessage" to jsonNullableString(errorMessage),
        ),
    )

    fun report(
        timestamp: Long,
        summary: String,
        findings: List<HistoryFinding>,
        suggestions: List<String>,
    ): HistoryRecord = HistoryRecord(
        timestamp = timestamp,
        type = HistoryType.REPORT,
        title = "Network Diagnostic Report",
        summary = summary,
        detailJson = jsonObject(
            "summary" to jsonString(summary),
            "findings" to findings.joinToString(prefix = "[", postfix = "]") { finding ->
                jsonObject(
                    "level" to jsonString(finding.level),
                    "title" to jsonString(finding.title),
                    "description" to jsonString(finding.description),
                )
            },
            "suggestions" to jsonStringArray(suggestions),
        ),
    )

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:$value"
        }

    private fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", transform = ::jsonString)

    private fun jsonNumber(value: Long?): String = value?.toString() ?: "null"

    private fun jsonDouble(value: Double?): String = value?.toString() ?: "null"

    private fun jsonNullableString(value: String?): String = value?.let(::jsonString) ?: "null"

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
