package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalysisResult
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticAnalyzerV4
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticOrchestrator
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageProgress
import java.util.concurrent.CancellationException
import javax.inject.Inject

data class AutomaticDiagnosticResult(
    val evidence: DiagnosticRunEvidence,
    val analysis: DiagnosticAnalysisResult,
)

/**
 * Application boundary for the automatic diagnostic pipeline.
 *
 * The orchestrator owns collection and the v4 analyzer owns interpretation.
 * History is written only after a complete run, so internal probes never
 * become individual records and cancelled/ambiguous runs are not presented as
 * completed reports.
 */
class RunAutomaticDiagnosticUseCase @Inject constructor(
    private val orchestrator: DiagnosticOrchestrator,
    private val analyzer: DiagnosticAnalyzerV4,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        intent: DiagnosticIntent = DiagnosticIntent(),
        onProgress: (DiagnosticStageProgress) -> Unit = {},
    ): AutomaticDiagnosticResult {
        val evidence = orchestrator.run(intent, onProgress)
        val analysis = analyzer.analyze(evidence)

        if (evidence.runStatus == DiagnosticRunStatus.COMPLETED) {
            try {
                historyRecorder.record(
                    AutomaticDiagnosticHistoryAdapter.toHistoryRecord(evidence, analysis),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A local history failure must not turn a completed diagnostic
                // into a failed network result. The Room recorder applies the
                // same supplementary-data policy at its own boundary.
            }
        }

        return AutomaticDiagnosticResult(evidence = evidence, analysis = analysis)
    }
}
