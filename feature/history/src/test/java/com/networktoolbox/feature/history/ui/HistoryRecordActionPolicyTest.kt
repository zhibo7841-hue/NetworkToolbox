package com.networktoolbox.feature.history.ui

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordActionPolicyTest {
    @Test
    fun reportWithRestorablePayloadShowsReportAction() {
        assertTrue(canShowReportAction(reportRecord(), canOpenReport = { true }))
    }

    @Test
    fun reportWithoutRestorablePayloadHidesReportAction() {
        assertFalse(canShowReportAction(reportRecord(), canOpenReport = { false }))
    }

    @Test
    fun nonReportRecordNeverShowsReportAction() {
        val ping = reportRecord().copy(type = HistoryType.PING)

        assertFalse(canShowReportAction(ping, canOpenReport = { true }))
    }

    private fun reportRecord(): HistoryRecord = HistoryRecord(
        timestamp = 1_000L,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = "网络状态正常",
        detailJson = "{}",
    )
}
