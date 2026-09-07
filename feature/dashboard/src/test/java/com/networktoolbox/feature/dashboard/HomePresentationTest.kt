package com.networktoolbox.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomePresentationTest {
    @Test
    fun homeUsesNetworkFirstOrderWithoutBrandHeader() {
        assertEquals(
            listOf("network", "diagnostic", "quick-tools", "recent-diagnosis"),
            HomePresentation.sectionOrder,
        )
        assertFalse(HomePresentation.sectionOrder.contains("brand-header"))
    }

    @Test
    fun emptyRecentDiagnosis_usesExplicitEmptyState() {
        assertEquals("暂无诊断记录", HomePresentation.recentDiagnosticBody(null))
        assertNull(HomePresentation.recentDiagnosticSummary(null))
    }

    @Test
    fun recentDiagnosis_usesRealRecordTitleAndSummary() {
        val preview = RecentHistoryPreview(
            type = "网络诊断",
            title = "网络状态正常",
            summary = "网关正常 · 公网正常 · DNS正常",
            timestamp = 1_000L,
        )

        assertEquals("网络状态正常", HomePresentation.recentDiagnosticBody(preview))
        assertEquals("网关正常 · 公网正常 · DNS正常", HomePresentation.recentDiagnosticSummary(preview))
    }

    @Test
    fun networkDetailsAction_usesAccessibleChevronLabels() {
        assertEquals("查看网络详情", HomePresentation.networkDetailsContentDescription(false))
        assertEquals("收起网络详情", HomePresentation.networkDetailsContentDescription(true))
    }
}
