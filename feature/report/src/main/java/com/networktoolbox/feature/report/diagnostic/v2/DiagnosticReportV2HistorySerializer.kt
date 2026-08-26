package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType

/** Serializes the complete v2 report into the existing generic local history payload. */
object DiagnosticReportV2HistorySerializer {
    fun toHistoryRecord(report: DiagnosticReportV2): HistoryRecord = HistoryRecord(
        timestamp = report.timestamp,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = report.summary,
        detailJson = jsonObject(
            "schemaVersion" to "2",
            "timestamp" to report.timestamp.toString(),
            "durationMs" to (report.durationMs?.toString() ?: "null"),
            "overallStatus" to jsonString(report.overallStatus.name),
            "overallSeverity" to jsonString(report.overallSeverity.name),
            "summary" to jsonString(report.summary),
            "historySummary" to jsonString(report.historySummary()),
            "networkSnapshot" to networkSnapshot(report),
            "checks" to report.checks.joinToString(prefix = "[", postfix = "]") { check ->
                jsonObject(
                    "id" to jsonString(check.id),
                    "stage" to jsonString(check.stage.name),
                    "name" to jsonString(check.name),
                    "status" to jsonString(check.status.name),
                    "severity" to jsonString(check.severity.name),
                    "summary" to jsonString(check.summary),
                    "target" to jsonNullableString(check.target),
                    "method" to jsonNullableString(check.method),
                    "observedAt" to (check.observedAt?.toString() ?: "null"),
                    "rawData" to stringMap(check.rawData),
                )
            },
            "findings" to report.findings.joinToString(prefix = "[", postfix = "]") { finding ->
                jsonObject(
                    "id" to jsonString(finding.id),
                    "severity" to jsonString(finding.severity.name),
                    "title" to jsonString(finding.title),
                    "description" to jsonString(finding.description),
                    "evidenceCheckIds" to jsonStringArray(finding.evidenceCheckIds),
                )
            },
            "recommendations" to report.recommendations.joinToString(
                prefix = "[",
                postfix = "]",
            ) { recommendation ->
                jsonObject(
                    "priority" to recommendation.priority.toString(),
                    "title" to jsonString(recommendation.title),
                    "action" to jsonString(recommendation.action),
                    "reason" to jsonNullableString(recommendation.reason),
                )
            },
        ),
    )

    private fun DiagnosticReportV2.historySummary(): String = listOf(
        checkSummary("GATEWAY_REACHABILITY", "网关"),
        checkSummary("PUBLIC_CONNECTIVITY", "公网"),
        checkSummary("DNS_RESOLUTION", "DNS"),
    ).joinToString(" · ")

    private fun DiagnosticReportV2.checkSummary(id: String, label: String): String {
        val check = checks.firstOrNull { it.id == id }
        val status = when (check?.status) {
            DiagnosticCheckStatus.PASS -> "正常"
            DiagnosticCheckStatus.FAIL -> "异常"
            DiagnosticCheckStatus.NO_RECORDS -> "无记录"
            DiagnosticCheckStatus.NOT_APPLICABLE -> "不适用"
            DiagnosticCheckStatus.SKIPPED -> "未执行"
            DiagnosticCheckStatus.UNKNOWN,
            null,
            -> "未确定"
        }
        return "$label$status"
    }

    private fun networkSnapshot(report: DiagnosticReportV2): String {
        val context = report.networkSnapshot ?: return "null"
        return jsonObject(
            "connectionType" to jsonString(context.connectionType.name),
            "activeNetworkAvailable" to (context.activeNetworkAvailable?.toString() ?: "null"),
            "validated" to (context.validated?.toString() ?: "null"),
            "ipv4Address" to jsonNullableString(context.ipv4Address),
            "ipv6Address" to jsonNullableString(context.ipv6Address),
            "gateway" to jsonNullableString(context.gateway),
            "dnsServers" to jsonStringArray(context.dnsServers),
            "vpnActive" to (context.vpnActive?.toString() ?: "null"),
            "wifiName" to jsonNullableString(context.wifiName),
            "wifiSignalLevel" to (context.wifiSignalLevel?.toString() ?: "null"),
        )
    }

    private fun stringMap(values: Map<String, String>): String = jsonObject(
        values.map { (key, value) -> key to jsonString(value) },
    )

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        jsonObject(fields.toList())

    private fun jsonObject(fields: List<Pair<String, String>>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:$value"
        }

    private fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", transform = ::jsonString)

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
