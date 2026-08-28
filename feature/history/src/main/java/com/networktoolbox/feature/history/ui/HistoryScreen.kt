package com.networktoolbox.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.PingHistorySummary
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.history.presentation.HistoryUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onLoad: () -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: (HistoryRecord) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack, enabled = uiState !is HistoryUiState.Loading) {
                Text("返回工具")
            }
            Text("历史记录", style = MaterialTheme.typography.headlineSmall)
            Text(
                "查看本机保存的网络检测记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val state = uiState) {
                HistoryUiState.Loading -> StatusCard("加载中...")
                HistoryUiState.Empty -> EmptyHistoryCard()
                is HistoryUiState.Error -> ErrorCard(state.message, onLoad)
                is HistoryUiState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("历史记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("清空")
                        }
                    }
                    state.records.forEach { record ->
                        HistoryRecordCard(
                            record = record,
                            onDelete = onDelete,
                            onOpenReport = onOpenReport,
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部历史记录？") },
            text = { Text("所有本地检测历史都会被删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun HistoryRecordCard(
    record: HistoryRecord,
    onDelete: (Long) -> Unit,
    onOpenReport: (HistoryRecord) -> Unit,
) {
    val pingDetails = if (record.type == HistoryType.PING) {
        record.pingDetails()
    } else {
        null
    }
    val dnsDetails = if (record.type == HistoryType.DNS) {
        record.dnsDetails()
    } else {
        null
    }
    val isDiagnosticV2 = record.type == HistoryType.REPORT &&
        record.detailJson.readJsonNumber("schemaVersion") == "2"
    val diagnosticHistorySummary = if (isDiagnosticV2) {
        record.detailJson.readJsonString("historySummary")
    } else {
        null
    }
    val displayTitle = when {
        isDiagnosticV2 -> "网络诊断"
        record.type == HistoryType.REPORT && record.title == "Network Diagnostic Report" -> "网络诊断"
        else -> pingDetails?.target ?: dnsDetails?.domain ?: record.title
    }
    val displaySummary = if (record.type == HistoryType.PING) {
        PingHistorySummary.fromQualityLevel(
            qualityLevel = pingDetails?.qualityLevel.orEmpty(),
            fallback = record.summary,
        )
    } else {
        dnsDetails?.summary ?: record.summary.localizedHistorySummary()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(record.type.displayName(), style = MaterialTheme.typography.titleMedium)
                Text(
                    record.timestamp.toDisplayTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(displayTitle, style = MaterialTheme.typography.bodyLarge)
            Text(displaySummary, style = MaterialTheme.typography.bodyMedium)
            diagnosticHistorySummary?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pingDetails?.metricsText()?.let { metrics ->
                Text(
                    metrics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dnsDetails?.metricsText()?.let { metrics ->
                Text(
                    metrics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isDiagnosticV2) {
                    TextButton(onClick = { onOpenReport(record) }) {
                        Text("查看报告")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onDelete(record.id) }) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("暂无历史记录", style = MaterialTheme.typography.titleMedium)
            Text("执行一次网络检测即可创建记录。")
        }
    }
}

@Composable
private fun StatusCard(status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(status, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("状态：失败", style = MaterialTheme.typography.titleMedium)
            Text(message)
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

private data class PingHistoryDetails(
    val target: String?,
    val qualityLevel: String?,
    val avgLatencyMs: Double?,
    val packetLoss: Double?,
)

private data class DnsHistoryDetails(
    val domain: String?,
    val summary: String?,
    val recordCounts: Map<String, Int>,
    val durationMs: Long?,
)

private fun HistoryRecord.pingDetails(): PingHistoryDetails? {
    val details = PingHistoryDetails(
        target = detailJson.readJsonString("target")
            ?: title.substringAfter(" · ", "").takeIf(String::isNotBlank),
        qualityLevel = detailJson.readJsonString("qualityLevel"),
        avgLatencyMs = detailJson.readJsonNumber("avgLatencyMs")?.toDoubleOrNull(),
        packetLoss = detailJson.readJsonNumber("packetLoss")?.toDoubleOrNull(),
    )
    return details.takeIf {
        it.target != null ||
            it.qualityLevel != null ||
            it.avgLatencyMs != null ||
            it.packetLoss != null
    }
}

private fun HistoryRecord.dnsDetails(): DnsHistoryDetails? {
    val details = DnsHistoryDetails(
        domain = detailJson.readJsonString("domain")
            ?: title.substringAfter(" · ", "").takeIf(String::isNotBlank),
        summary = detailJson.readJsonString("summary"),
        recordCounts = DNS_RECORD_TYPES.mapNotNull { type ->
            detailJson.readJsonObjectNumber("recordCounts", type)?.let { count ->
                type to count
            }
        }.toMap(),
        durationMs = detailJson.readJsonNumber("durationMs")?.toLongOrNull(),
    )
    return details.takeIf {
        it.domain != null || it.summary != null || it.recordCounts.isNotEmpty() || it.durationMs != null
    }
}

private fun PingHistoryDetails.metricsText(): String? = buildList {
    avgLatencyMs?.let { add("平均 ${it.toCompactNumber()} ms") }
    packetLoss?.let { add("丢包 ${it.toCompactPercentage()}%") }
}.joinToString(" · ").takeIf(String::isNotBlank)

private fun DnsHistoryDetails.metricsText(): String? = buildList {
    DNS_RECORD_TYPES.forEach { type ->
        recordCounts[type]?.takeIf { it > 0 }?.let { count -> add("$type $count 条") }
    }
    if (recordCounts.values.any { it > 0 }) {
        durationMs?.let { add("$it ms") }
    }
}.joinToString(" · ").takeIf(String::isNotBlank)

private fun String.localizedHistorySummary(): String = when (this) {
    "DNS lookup completed" -> "DNS 查询完成"
    "Ping completed" -> "Ping 检测完成"
    "Ping failed" -> "Ping 检测失败"
    "TCP port check completed" -> "TCP 端口检测完成"
    "TCP port check failed" -> "TCP 端口检测失败"
    else -> this
}

private fun String.readJsonString(key: String): String? {
    val marker = "\"$key\":\""
    val valueStart = indexOf(marker)
        .takeIf { it >= 0 }
        ?.plus(marker.length)
        ?: return null
    val value = StringBuilder()
    var index = valueStart
    while (index < length) {
        when (val character = this[index]) {
            '\"' -> return value.toString()
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
                index += 1
            }
        }
    }
    return null
}

private fun String.readJsonNumber(key: String): String? {
    val marker = "\"$key\":"
    val valueStart = indexOf(marker)
        .takeIf { it >= 0 }
        ?.plus(marker.length)
        ?: return null
    val valueEnd = indexOfAny(charArrayOf(',', '}'), valueStart)
    val value = substring(valueStart, if (valueEnd >= 0) valueEnd else length).trim()
    return value.takeUnless { it == "null" || it.isBlank() }
}

private fun String.readJsonObjectNumber(objectKey: String, key: String): Int? {
    val objectMarker = "\"$objectKey\":{"
    val objectStart = indexOf(objectMarker)
    if (objectStart < 0) return null
    val marker = "\"$key\":"
    val valueStart = indexOf(marker, objectStart + objectMarker.length)
    if (valueStart < 0) return null
    val numberStart = valueStart + marker.length
    val valueEnd = indexOfAny(charArrayOf(',', '}'), numberStart)
    val value = substring(numberStart, if (valueEnd >= 0) valueEnd else length).trim()
    return value.toIntOrNull()
}

private fun Double.toCompactNumber(): String =
    if (this == toLong().toDouble()) {
        toLong().toString()
    } else {
        "%.1f".format(java.util.Locale.US, this)
    }

private fun Double.toCompactPercentage(): String = toCompactNumber()

private val DNS_RECORD_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT")

private fun HistoryType.displayName(): String = when (this) {
    HistoryType.PING -> "Ping"
    HistoryType.DNS -> "DNS 查询"
    HistoryType.TCP -> "TCP 端口检测"
    HistoryType.REPORT -> "网络诊断"
    HistoryType.LAN_SCAN -> "局域网扫描"
    HistoryType.UNKNOWN -> "其他"
}

private fun Long.toDisplayTime(): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(this).atZone(zone)
    val today = LocalDate.now(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm").format(dateTime)

    return when (dateTime.toLocalDate()) {
        today -> "今天 $time"
        today.minusDays(1) -> "昨天 $time"
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dateTime)
    }
}
