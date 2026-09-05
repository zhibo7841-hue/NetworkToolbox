package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.feature.report.diagnostic.v2.AutomaticDiagnosticHistorySnapshotSerializer
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult

/**
 * Single write boundary for completed v0.4 automatic diagnostics.
 *
 * Legacy v1/v2 history readers remain available, but new v0.4 records retain
 * the original evidence and analysis instead of projecting them into the old
 * report envelope first.
 */
internal object AutomaticDiagnosticHistoryAdapter {
    fun toHistoryRecord(
        evidence: DiagnosticRunEvidence,
        analysis: DiagnosticAnalysisResult,
    ): HistoryRecord = AutomaticDiagnosticHistorySnapshotSerializer.toHistoryRecord(
        AutomaticDiagnosticResult(evidence = evidence, analysis = analysis),
    )
}
