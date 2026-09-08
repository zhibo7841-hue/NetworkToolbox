package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.StatusVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryRecordPresentationTest {
    @Test
    fun schema3NormalReportUsesStoredDiagnosisStatus() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """
                    {"schemaVersion":3,"analysis":{"diagnosis":{"status":"NORMAL"}},"summary":"任何文字都不参与判断"}
                """.trimIndent(),
            ),
        )

        assertEquals(StatusVisualState.NORMAL, visual.state)
        assertEquals("正常", visual.label)
    }

    @Test
    fun schema3AttentionReportUsesAmberNotice() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """
                    {"analysis":{"diagnosis":{"status":"ATTENTION"}}}
                """.trimIndent(),
            ),
        )

        assertEquals(StatusVisualState.NOTICE, visual.state)
        assertEquals("需要关注", visual.label)
    }

    @Test
    fun schema3ErrorReportUsesRedError() {
        val visual = HistoryRecordPresentation.status(
            report(detailJson = """{"analysis":{"diagnosis":{"status":"ERROR"}}}"""),
        )

        assertEquals(StatusVisualState.ERROR, visual.state)
        assertEquals("严重异常", visual.label)
    }

    @Test
    fun schema2HealthyReportUsesStoredOverallStatus() {
        val visual = HistoryRecordPresentation.status(
            report(detailJson = """{"schemaVersion":2,"overallStatus":"HEALTHY"}"""),
        )

        assertEquals(StatusVisualState.NORMAL, visual.state)
    }

    @Test
    fun legacyReportWithoutStructuredStatusRemainsUnknown() {
        val visual = HistoryRecordPresentation.status(
            report(
                detailJson = """{"summary":"网络状态正常","findings":[]}""",
                summary = "网络状态正常",
            ),
        )

        assertEquals(StatusVisualState.UNKNOWN, visual.state)
        assertEquals("未确定", visual.label)
    }

    @Test
    fun pingSessionQualityLevelDrivesToolScopedStatus() {
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, "正常"),
            HistoryRecordPresentation.status(
                record(
                    HistoryType.PING,
                    """{"qualityLevel":"EXCELLENT","sentPackets":5,"receivedPackets":5,"packetLoss":0.0}""",
                ),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, "正常"),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"GOOD"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, "需关注"),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"FAIR"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, "需关注"),
            HistoryRecordPresentation.status(
                record(HistoryType.PING, """{"qualityLevel":"POOR"}"""),
            ),
        )
    }

    @Test
    fun pingNoResponseAndUnknownAreDistinguishedWithoutSummaryParsing() {
        val noResponse = HistoryRecordPresentation.status(
            record(
                HistoryType.PING,
                """{"qualityLevel":"UNKNOWN","sentPackets":5,"receivedPackets":0,"packetLoss":100.0}""",
                summary = "网络连接稳定，未检测到明显丢包。",
            ),
        )
        val insufficient = HistoryRecordPresentation.status(
            record(
                HistoryType.PING,
                """{"qualityLevel":"UNKNOWN","sentPackets":0,"receivedPackets":0}""",
                summary = "网络质量较差，检测到明显延迟或丢包。",
            ),
        )

        assertEquals(HistoryStatusVisual(StatusVisualState.NOTICE, "未响应"), noResponse)
        assertEquals(HistoryStatusVisual(StatusVisualState.UNKNOWN, "未确定"), insufficient)
    }

    @Test
    fun pingCancellationUsesNeutralStructuredStatus() {
        val visual = HistoryRecordPresentation.status(
            record(HistoryType.PING, """{"status":"CANCELLED"}"""),
        )

        assertEquals(StatusVisualState.CANCELLED, visual.state)
        assertEquals("已停止", visual.label)
    }

    @Test
    fun legacyToolRecordsRemainConservativeWhenFailureReasonIsMissing() {
        assertEquals(
            StatusVisualState.NORMAL,
            HistoryRecordPresentation.status(
                record(HistoryType.PING, "{\"success\":true}"),
            ).state,
        )
        assertEquals(
            StatusVisualState.UNKNOWN,
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, "{\"success\":false}"),
            ).state,
        )
        assertEquals(
            StatusVisualState.NOTICE,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"status\":\"NO_RECORDS\"}"),
            ).state,
        )
        assertEquals(
            StatusVisualState.NORMAL,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"success\":true}"),
            ).state,
        )
        assertEquals(
            StatusVisualState.UNKNOWN,
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, "{\"success\":false}"),
            ).state,
        )
    }

    @Test
    fun dnsStructuredStatusesStayScopedToDnsLookup() {
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, "正常"),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"SUCCESS"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.WARNING, "域名不存在"),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"NXDOMAIN"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, "严重异常"),
            HistoryRecordPresentation.status(
                record(HistoryType.DNS, """{"status":"TIMEOUT"}"""),
            ),
        )
    }

    @Test
    fun tcpOutcomePreservesTargetScopedSemantics() {
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NORMAL, "正常"),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"CONNECT_SUCCESS"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, "需关注"),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"CONNECTION_REFUSED"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.NOTICE, "未响应"),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"TIMEOUT"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, "无法到达"),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"NO_ROUTE"}"""),
            ),
        )
        assertEquals(
            HistoryStatusVisual(StatusVisualState.ERROR, "无法到达"),
            HistoryRecordPresentation.status(
                record(HistoryType.TCP, """{"outcome":"NETWORK_UNREACHABLE"}"""),
            ),
        )
    }

    @Test
    fun malformedToolPayloadsSafelyRemainUnknown() {
        listOf(HistoryType.PING, HistoryType.DNS, HistoryType.TCP).forEach { type ->
            assertEquals(
                HistoryStatusVisual(StatusVisualState.UNKNOWN, "未确定"),
                HistoryRecordPresentation.status(record(type, "not-json")),
            )
        }
    }

    @Test
    fun reportNetworkLabelComesFromStoredNetworkSummary() {
        val label = HistoryRecordPresentation.networkLabel(
            report(
                detailJson = """
                    {"evidence":{"networkSummary":{"connectionType":"CELLULAR"}}}
                """.trimIndent(),
            ),
        )

        assertEquals("移动网络", label)
    }

    @Test
    fun nonReportRecordsDoNotInventNetworkLabel() {
        assertTrue(
            HistoryRecordPresentation.networkLabel(
                record(HistoryType.PING, "{\"success\":true}"),
            ) == null,
        )
    }

    @Test
    fun cardContentKeepsSummaryAndMetadataWithoutDuplicateTitle() {
        val content = HistoryRecordPresentation.cardContent(
            typeTitle = "网络诊断",
            titleCandidate = "网络诊断",
            summary = "发现 DNS 异常",
            metadata = listOf("Wi-Fi", "公网正常 · DNS异常"),
        )

        assertNull(content.secondaryTitle)
        assertEquals("网络诊断", content.title)
        assertEquals("发现 DNS 异常", content.summary)
        assertEquals("Wi-Fi · 公网正常 · DNS异常", content.metadata)
    }

    @Test
    fun cardContentKeepsNonReportTargetAsSecondaryTitle() {
        val content = HistoryRecordPresentation.cardContent(
            typeTitle = "Ping",
            titleCandidate = "10.0.1.122",
            summary = "网络连接稳定",
            metadata = listOf("平均 16 ms", "丢包 0%"),
        )

        assertEquals("10.0.1.122", content.secondaryTitle)
        assertEquals("平均 16 ms · 丢包 0%", content.metadata)
    }

    @Test
    fun timeLabelUsesExistingTodayYesterdayAndAbsoluteFormats() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 9, 8)

        assertEquals(
            "今天 19:07",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-08T19:07:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
        assertEquals(
            "昨天 22:10",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-07T22:10:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
        assertEquals(
            "2026-09-01 08:00",
            HistoryRecordPresentation.timeLabel(
                Instant.parse("2026-09-01T08:00:00Z").toEpochMilli(),
                zone = zone,
                today = today,
            ),
        )
    }

    private fun report(
        detailJson: String,
        summary: String = "网络诊断",
    ) = record(HistoryType.REPORT, detailJson, summary)

    private fun record(
        type: HistoryType,
        detailJson: String,
        summary: String = "摘要",
    ) = HistoryRecord(
        id = 1L,
        timestamp = 1_000L,
        type = type,
        title = "测试记录",
        summary = summary,
        detailJson = detailJson,
    )
}
