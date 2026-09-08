package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.designsystem.StatusVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
