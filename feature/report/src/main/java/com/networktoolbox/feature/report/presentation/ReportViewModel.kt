package com.networktoolbox.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.common.diagnostic.DiagnosticIntent
import com.networktoolbox.core.common.diagnostic.DiagnosticRunStatus
import com.networktoolbox.core.common.diagnostic.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageProgress as LegacyStageProgress
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageState as LegacyStageState
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationComparator
import com.networktoolbox.feature.report.diagnostic.v4.DiagnosticVerificationResult
import com.networktoolbox.feature.report.domain.AutomaticDiagnosticResult
import com.networktoolbox.feature.report.domain.RunAutomaticDiagnosticUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val status: ReportStatus = ReportStatus.Idle,
)

/** UI lifecycle state. It is intentionally separate from domain run statuses. */
sealed interface ReportStatus {
    data object Idle : ReportStatus

    data class Running(
        val progress: ReportProgress = ReportProgress(),
    ) : ReportStatus

    data class Completed(
        val result: AutomaticDiagnosticResult,
        val comparison: DiagnosticVerificationResult? = null,
    ) : ReportStatus

    data object Cancelled : ReportStatus

    data class NetworkChanged(
        val result: AutomaticDiagnosticResult,
    ) : ReportStatus

    data class Failed(
        val result: AutomaticDiagnosticResult? = null,
        val message: String = "诊断流程未能完整完成。",
    ) : ReportStatus

    /** Kept for old callers and restored schema-2 reports during migration. */
    @Deprecated("Use Completed for live automatic diagnostics.")
    data class Success(
        val report: DiagnosticReportV2,
    ) : ReportStatus

    /** Kept for source compatibility with the pre-v4 screen contract. */
    @Deprecated("Use Failed for live automatic diagnostics.")
    data class Error(
        val message: String,
    ) : ReportStatus
}

data class ReportProgress(
    val stageStates: Map<DiagnosticStage, ReportStageStatus> = diagnosticStages.associateWith {
        ReportStageStatus.PENDING
    },
    val activeStage: DiagnosticStage? = null,
) {
    fun apply(
        progress: com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageProgress,
    ): ReportProgress {
        val nextStates = stageStates.toMutableMap()
        nextStates[progress.stage] = progress.state.toUiStatus()
        return copy(
            stageStates = nextStates,
            activeStage = progress.stage.takeIf {
                progress.state == com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.RUNNING
            },
        )
    }

    /** Compatibility mapper for the retained pre-v4 pipeline. */
    @Suppress("UNUSED")
    fun apply(progress: LegacyStageProgress): ReportProgress {
        val stage = progress.stage.toCurrentStage() ?: return this
        val nextStates = stageStates.toMutableMap()
        nextStates[stage] = when (progress.state) {
            LegacyStageState.RUNNING -> ReportStageStatus.RUNNING
            LegacyStageState.COMPLETED -> ReportStageStatus.COMPLETED
            LegacyStageState.FAILED -> ReportStageStatus.FAILED
            LegacyStageState.SKIPPED -> ReportStageStatus.SKIPPED
        }
        return copy(
            stageStates = nextStates,
            activeStage = stage.takeIf { progress.state == LegacyStageState.RUNNING },
        )
    }
}

enum class ReportStageStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    NOT_APPLICABLE,
    UNKNOWN,
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val runDiagnostic: RunAutomaticDiagnosticUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var runGeneration = 0L
    private var cancellationRequested = false

    fun runCheck() {
        if (_uiState.value.status is ReportStatus.Running) return

        val previousResult = (_uiState.value.status as? ReportStatus.Completed)?.result
        val generation = ++runGeneration
        cancellationRequested = false
        _uiState.value = ReportUiState(status = ReportStatus.Running())

        activeJob = viewModelScope.launch {
            try {
                val result = runDiagnostic(DiagnosticIntent()) { stageProgress ->
                    if (generation != runGeneration || cancellationRequested) return@runDiagnostic
                    _uiState.update { state ->
                        val running = state.status as? ReportStatus.Running
                            ?: return@update state
                        state.copy(
                            status = running.copy(
                                progress = running.progress.apply(stageProgress),
                            ),
                        )
                    }
                }

                if (generation != runGeneration || cancellationRequested) return@launch
                _uiState.value = ReportUiState(
                    status = when (result.evidence.runStatus) {
                        DiagnosticRunStatus.COMPLETED -> ReportStatus.Completed(
                            result = result,
                            comparison = previousResult?.let {
                                DiagnosticVerificationComparator.compare(it, result)
                            },
                        )
                        DiagnosticRunStatus.NETWORK_CHANGED -> ReportStatus.NetworkChanged(result)
                        DiagnosticRunStatus.CANCELLED -> ReportStatus.Cancelled
                        DiagnosticRunStatus.FAILED -> ReportStatus.Failed(
                            result = result,
                            message = "诊断流程未能完整完成。",
                        )
                        DiagnosticRunStatus.RUNNING -> ReportStatus.Failed(
                            result = result,
                            message = "诊断仍在进行，暂时无法生成结果。",
                        )
                    },
                )
            } catch (error: CancellationException) {
                if (!cancellationRequested) throw error
            } catch (error: Exception) {
                if (generation == runGeneration && !cancellationRequested) {
                    _uiState.value = ReportUiState(
                        ReportStatus.Failed(
                            message = error.message?.takeIf(String::isNotBlank)
                                ?: "诊断流程不可用。",
                        ),
                    )
                }
            } finally {
                if (generation == runGeneration) activeJob = null
            }
        }
    }

    fun stopCheck() {
        if (_uiState.value.status !is ReportStatus.Running) return

        cancellationRequested = true
        runGeneration++
        activeJob?.cancel()
        activeJob = null
        _uiState.value = ReportUiState(ReportStatus.Cancelled)
    }
}

val diagnosticStages: List<DiagnosticStage> = listOf(
    DiagnosticStage.NETWORK_STATE,
    DiagnosticStage.IP_CONFIGURATION,
    DiagnosticStage.GATEWAY,
    DiagnosticStage.INTERNET,
    DiagnosticStage.DNS,
    DiagnosticStage.TARGET,
)

private fun com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.toUiStatus(): ReportStageStatus = when (this) {
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.PENDING -> ReportStageStatus.PENDING
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.RUNNING -> ReportStageStatus.RUNNING
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.COMPLETED -> ReportStageStatus.COMPLETED
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.SKIPPED -> ReportStageStatus.SKIPPED
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.NOT_APPLICABLE -> ReportStageStatus.NOT_APPLICABLE
    com.networktoolbox.feature.report.diagnostic.v2.orchestration.DiagnosticStageState.UNKNOWN -> ReportStageStatus.UNKNOWN
}

private fun com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.toCurrentStage(): DiagnosticStage? = when (this) {
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.NETWORK_CONTEXT -> DiagnosticStage.NETWORK_STATE
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.GATEWAY -> DiagnosticStage.GATEWAY
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.PUBLIC_CONNECTIVITY -> DiagnosticStage.INTERNET
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.DNS -> DiagnosticStage.DNS
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.DOMAIN_CONNECTIVITY -> DiagnosticStage.TARGET
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.NETWORK_CHANGED -> DiagnosticStage.NETWORK_STATE
    com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage.ANALYSIS -> null
}
