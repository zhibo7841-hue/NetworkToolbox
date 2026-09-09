package com.networktoolbox.feature.report.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPresentationContextTest {
    @Test
    fun liveAndSavedReportsKeepDistinctTitles() {
        assertEquals("网络诊断", ReportPresentationContext.LIVE_TOOL.title)
        assertEquals("网络诊断报告", ReportPresentationContext.SAVED_REPORT.title)
    }
}
