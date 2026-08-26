package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticAnalyzerV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticPipeline
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2HistorySerializer
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageProgress
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageState
import javax.inject.Inject

class RunDiagnosticV2UseCase @Inject constructor(
    private val pipeline: DiagnosticPipeline,
    private val analyzer: DiagnosticAnalyzerV2,
    private val historyRecorder: HistoryRecorder,
) {
    /**
     * A report is recorded only after the complete pipeline has returned and
     * analysis has succeeded. Cancellation therefore cannot save a completed
     * report or any internal probe result.
     */
    suspend operator fun invoke(
        onStageChanged: (DiagnosticStageProgress) -> Unit = {},
    ): DiagnosticReportV2 {
        val pipelineResult = pipeline.run(onStageChanged)
        onStageChanged(
            DiagnosticStageProgress(
                stage = DiagnosticStage.ANALYSIS,
                state = DiagnosticStageState.RUNNING,
            ),
        )
        val report = analyzer.analyze(pipelineResult)
        historyRecorder.record(DiagnosticReportV2HistorySerializer.toHistoryRecord(report))
        onStageChanged(
            DiagnosticStageProgress(
                stage = DiagnosticStage.ANALYSIS,
                state = DiagnosticStageState.COMPLETED,
            ),
        )
        return report
    }
}
