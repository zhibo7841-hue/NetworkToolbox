package com.networktoolbox.feature.report.domain

import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticOrchestrator
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticRunEvidence
import com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageProgress
import javax.inject.Inject

/** Application boundary for the Task 050 evidence orchestration adapter. */
class RunAutomaticDiagnosticUseCase @Inject constructor(
    private val orchestrator: DiagnosticOrchestrator,
) {
    suspend operator fun invoke(
        intent: DiagnosticIntent = DiagnosticIntent(),
        onProgress: (DiagnosticStageProgress) -> Unit = {},
    ): DiagnosticRunEvidence = orchestrator.run(intent, onProgress)
}
