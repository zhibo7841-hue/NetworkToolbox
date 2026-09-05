package com.networktoolbox.feature.report.diagnostic.v2

import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticHistoryReportResolverTest {
    @Test
    fun schema3AutomaticSnapshotIsActionableAndRestored() {
        val original = automaticResult()
        val record = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(original)

        val resolved = DiagnosticHistoryReportResolver.resolve(record)

        assertTrue(resolved is ResolvedDiagnosticHistory.Automatic)
        assertTrue(DiagnosticHistoryReportResolver.canOpen(record))
        assertEquals(original, (resolved as ResolvedDiagnosticHistory.Automatic).result)
    }

    @Test
    fun schema2ReportRemainsActionableThroughCompatibilityReader() {
        val report = DiagnosticReportV2(
            timestamp = 2_000L,
            durationMs = 25L,
            overallStatus = DiagnosticOverallStatus.HEALTHY,
            overallSeverity = DiagnosticSeverity.HEALTHY,
            summary = "网络状态正常",
            networkSnapshot = null,
            checks = emptyList(),
            findings = emptyList(),
            recommendations = emptyList(),
        )
        val record = DiagnosticReportV2HistorySerializer.toHistoryRecord(report)

        val resolved = DiagnosticHistoryReportResolver.resolve(record)

        assertTrue(resolved is ResolvedDiagnosticHistory.Legacy)
        assertTrue(DiagnosticHistoryReportResolver.canOpen(record))
        assertEquals(report, (resolved as ResolvedDiagnosticHistory.Legacy).report)
    }

    @Test
    fun unsupportedAndMalformedReportsAreNotActionable() {
        val malformedV3 = reportRecord(
            detailJson = "{\"schemaVersion\":3,\"payloadType\":\"AUTOMATIC_DIAGNOSTIC_V4\"}",
        )
        val malformedV2 = reportRecord(detailJson = "{\"schemaVersion\":2}")
        val nonReport = HistoryRecord(
            timestamp = 3_000L,
            type = HistoryType.PING,
            title = "Ping",
            summary = "完成",
            detailJson = "{}",
        )

        assertFalse(DiagnosticHistoryReportResolver.canOpen(malformedV3))
        assertFalse(DiagnosticHistoryReportResolver.canOpen(malformedV2))
        assertFalse(DiagnosticHistoryReportResolver.canOpen(nonReport))
        assertEquals(null, DiagnosticHistoryReportResolver.resolve(malformedV3))
    }

    @Test
    fun unversionedLegacySummaryDoesNotClaimAReportAction() {
        val record = reportRecord(
            detailJson = "{\"summary\":\"网络状态正常\",\"findings\":[],\"suggestions\":[]}",
        )

        assertFalse(DiagnosticHistoryReportResolver.canOpen(record))
    }

    private fun automaticResult(): AutomaticDiagnosticResult = AutomaticDiagnosticResult(
        evidence = DiagnosticRunEvidence(
            runStatus = DiagnosticRunStatus.COMPLETED,
            startedAt = 1_000L,
            finishedAt = 1_001L,
            durationMs = 1L,
            fingerprint = null,
            networkContextSummary = null,
            observations = emptyList(),
            checks = emptyList(),
            intent = DiagnosticIntent(),
        ),
        analysis = DiagnosticAnalysisResult(
            findings = emptyList(),
            diagnosis = null,
            recommendations = emptyList(),
        ),
    )

    private fun reportRecord(detailJson: String): HistoryRecord = HistoryRecord(
        timestamp = 4_000L,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = "网络状态正常",
        detailJson = detailJson,
    )
}
