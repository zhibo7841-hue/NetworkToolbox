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
    fun toolStatusesUseStructuredSuccessOrDnsStatus() {
        assertEquals(
            StatusVisualState.NORMAL,
            HistoryRecordPresentation.status(
                record(HistoryType.PING, "{\"success\":true}"),
            ).state,
        )
        assertEquals(
            StatusVisualState.WARNING,
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
