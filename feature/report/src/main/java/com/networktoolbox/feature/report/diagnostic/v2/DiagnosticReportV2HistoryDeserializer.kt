package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext

/** Restores a version 2 report without adding a JSON dependency to the app. */
object DiagnosticReportV2HistoryDeserializer {
    fun fromHistoryRecord(record: HistoryRecord): DiagnosticReportV2? {
        if (record.type != HistoryType.REPORT) return null
        return fromDetailJson(record.detailJson)
    }

    fun fromDetailJson(detailJson: String): DiagnosticReportV2? = runCatching {
        val root = JsonParser(detailJson).parse() as? JsonObject ?: return null
        if (root.scalar("schemaVersion") != "2") return null

        DiagnosticReportV2(
            timestamp = root.long("timestamp") ?: return null,
            durationMs = root.longOrNull("durationMs"),
            overallStatus = root.enum<DiagnosticOverallStatus>("overallStatus") ?: return null,
            overallSeverity = root.enum<DiagnosticSeverity>("overallSeverity") ?: return null,
            summary = root.string("summary") ?: return null,
            networkSnapshot = root.obj("networkSnapshot")?.toNetworkContext(),
            checks = root.array("checks")?.values.orEmpty().mapNotNull { it.toCheck() },
            findings = root.array("findings")?.values.orEmpty().mapNotNull { it.toFinding() },
            recommendations = root.array("recommendations")?.values.orEmpty()
                .mapNotNull { it.toRecommendation() },
        )
    }.getOrNull()

    private fun JsonObject.toNetworkContext(): NetworkContext? {
        val connectionType = enum<ConnectionType>("connectionType") ?: return null
        return NetworkContext(
            connectionType = connectionType,
            activeNetworkAvailable = booleanOrNull("activeNetworkAvailable"),
            validated = booleanOrNull("validated"),
            ipv4Address = nullableString("ipv4Address"),
            ipv6Address = nullableString("ipv6Address"),
            gateway = nullableString("gateway"),
            dnsServers = array("dnsServers")?.values.orEmpty().mapNotNull { it.asString() },
            vpnActive = booleanOrNull("vpnActive"),
            wifiName = nullableString("wifiName"),
            wifiSignalLevel = intOrNull("wifiSignalLevel"),
        )
    }

    private fun JsonValue.toCheck(): DiagnosticCheck? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticCheck(
            id = objectValue.string("id") ?: return null,
            stage = objectValue.enum<DiagnosticStage>("stage") ?: return null,
            name = objectValue.string("name") ?: return null,
            status = objectValue.enum<DiagnosticCheckStatus>("status") ?: return null,
            severity = objectValue.enum<DiagnosticSeverity>("severity") ?: return null,
            summary = objectValue.string("summary") ?: return null,
            target = objectValue.nullableString("target"),
            method = objectValue.nullableString("method"),
            observedAt = objectValue.longOrNull("observedAt"),
            rawData = objectValue.obj("rawData")?.values.orEmpty().mapNotNull { (key, value) ->
                value.asScalarText()?.let { key to it }
            }.toMap(),
        )
    }

    private fun JsonValue.toFinding(): DiagnosticFindingV2? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticFindingV2(
            id = objectValue.string("id") ?: return null,
            severity = objectValue.enum<DiagnosticSeverity>("severity") ?: return null,
            title = objectValue.string("title") ?: return null,
            description = objectValue.string("description") ?: return null,
            evidenceCheckIds = objectValue.array("evidenceCheckIds")?.values.orEmpty()
                .mapNotNull { it.asString() },
        )
    }

    private fun JsonValue.toRecommendation(): DiagnosticRecommendation? {
        val objectValue = this as? JsonObject ?: return null
        return DiagnosticRecommendation(
            priority = objectValue.intOrNull("priority") ?: return null,
            title = objectValue.string("title") ?: return null,
            action = objectValue.string("action") ?: return null,
            reason = objectValue.nullableString("reason"),
        )
    }

    private fun JsonObject.string(key: String): String? = values[key]?.asString()

    private fun JsonObject.scalar(key: String): String? = values[key]?.asScalarText()

    private fun JsonObject.nullableString(key: String): String? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asString()

    private fun JsonObject.long(key: String): Long? = values[key]?.asNumber()?.toLongOrNull()

    private fun JsonObject.longOrNull(key: String): Long? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asNumber()
        ?.toLongOrNull()

    private fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

    private fun JsonObject.booleanOrNull(key: String): Boolean? = values[key]
        ?.takeUnless { it is JsonNull }
        ?.asBoolean()

    private fun JsonObject.obj(key: String): JsonObject? = values[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = values[key] as? JsonArray

    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T? =
        string(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun JsonValue.asString(): String? = (this as? JsonString)?.value

    private fun JsonValue.asNumber(): String? = (this as? JsonNumber)?.value

    private fun JsonValue.asBoolean(): Boolean? = (this as? JsonBoolean)?.value

    private fun JsonValue.asScalarText(): String? = when (this) {
        is JsonString -> value
        is JsonNumber -> value
        is JsonBoolean -> value.toString()
        JsonNull -> null
        is JsonArray,
        is JsonObject,
        -> null
    }
}

private sealed interface JsonValue

private data class JsonObject(val values: Map<String, JsonValue>) : JsonValue

private data class JsonArray(val values: List<JsonValue>) : JsonValue

private data class JsonString(val value: String) : JsonValue

private data class JsonNumber(val value: String) : JsonValue

private data class JsonBoolean(val value: Boolean) : JsonValue

private data object JsonNull : JsonValue

private class JsonParser(
    private val source: String,
) {
    private var index = 0

    fun parse(): JsonValue {
        require(source.length <= MAX_INPUT_LENGTH) { "JSON payload is too large." }
        val result = parseValue(depth = 0)
        skipWhitespace()
        require(index == source.length) { "Unexpected JSON data." }
        return result
    }

    private fun parseValue(depth: Int): JsonValue {
        require(depth <= MAX_DEPTH) { "JSON nesting is too deep." }
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON." }
        return when (source[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '"' -> JsonString(parseString())
            't' -> parseLiteral("true", JsonBoolean(true))
            'f' -> parseLiteral("false", JsonBoolean(false))
            'n' -> parseLiteral("null", JsonNull)
            '-', in '0'..'9' -> JsonNumber(parseNumber())
            else -> error("Invalid JSON value.")
        }
    }

    private fun parseObject(depth: Int): JsonObject {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consumeIf('}')) return JsonObject(values)

        while (true) {
            skipWhitespace()
            require(peek() == '"') { "Object key must be a string." }
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue(depth)
            skipWhitespace()
            when {
                consumeIf('}') -> return JsonObject(values)
                consumeIf(',') -> Unit
                else -> error("Invalid JSON object separator.")
            }
        }
    }

    private fun parseArray(depth: Int): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consumeIf(']')) return JsonArray(values)

        while (true) {
            values += parseValue(depth)
            skipWhitespace()
            when {
                consumeIf(']') -> return JsonArray(values)
                consumeIf(',') -> Unit
                else -> error("Invalid JSON array separator.")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    require(index < source.length) { "Invalid JSON escape." }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length) { "Invalid unicode escape." }
                            val hex = source.substring(index, index + 4)
                            result.append(hex.toIntOrNull(16)?.toChar() ?: error("Invalid unicode escape."))
                            index += 4
                        }

                        else -> error("Invalid JSON escape.")
                    }
                }

                else -> {
                    require(character.code >= 0x20) { "Invalid control character." }
                    result.append(character)
                }
            }
        }
        error("Unterminated JSON string.")
    }

    private fun parseNumber(): String {
        val start = index
        if (consumeIf('-')) Unit
        require(index < source.length) { "Invalid JSON number." }
        if (consumeIf('0')) {
            require(index >= source.length || source[index] !in '0'..'9') {
                "Invalid JSON number."
            }
        } else {
            require(consumeDigits()) { "Invalid JSON number." }
        }
        if (consumeIf('.')) require(consumeDigits()) { "Invalid JSON number." }
        if (index < source.length && source[index] in "eE") {
            index++
            if (index < source.length && source[index] in "+-") index++
            require(consumeDigits()) { "Invalid JSON number." }
        }
        return source.substring(start, index)
    }

    private fun consumeDigits(): Boolean {
        val start = index
        while (index < source.length && source[index] in '0'..'9') index++
        return index > start
    }

    private fun parseLiteral(expected: String, value: JsonValue): JsonValue {
        require(source.startsWith(expected, index)) { "Invalid JSON literal." }
        index += expected.length
        return value
    }

    private fun expect(expected: Char) {
        require(consumeIf(expected)) { "Expected '$expected'." }
    }

    private fun consumeIf(expected: Char): Boolean =
        if (index < source.length && source[index] == expected) {
            index++
            true
        } else {
            false
        }

    private fun peek(): Char = source.getOrNull(index) ?: error("Unexpected end of JSON.")

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private companion object {
        const val MAX_DEPTH = 32
        const val MAX_INPUT_LENGTH = 1_000_000
    }
}
