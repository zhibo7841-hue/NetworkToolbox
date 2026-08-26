package com.networktoolbox.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticReportV2
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStage
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageProgress
import com.networktoolbox.feature.report.diagnostic.v2.DiagnosticStageState
import com.networktoolbox.feature.report.domain.RunDiagnosticV2UseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val status: ReportStatus = ReportStatus.Idle,
)

sealed interface ReportStatus {
    data object Idle : ReportStatus

    data class Running(
        val progress: ReportProgress = ReportProgress(),
    ) : ReportStatus

    data class Success(
        val report: DiagnosticReportV2,
    ) : ReportStatus

    data object Cancelled : ReportStatus

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
    fun apply(progress: DiagnosticStageProgress): ReportProgress {
        val nextStates = stageStates.toMutableMap()
        nextStates[progress.stage] = progress.state.toUiStatus()
        return copy(
            stageStates = nextStates,
            activeStage = progress.stage.takeIf { progress.state == DiagnosticStageState.RUNNING },
        )
    }
}

enum class ReportStageStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val runDiagnostic: RunDiagnosticV2UseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var activeJob: kotlinx.coroutines.Job? = null
    private var cancellationRequested = false

    fun runCheck() {
        if (_uiState.value.status is ReportStatus.Running) return

        cancellationRequested = false
        _uiState.value = ReportUiState(status = ReportStatus.Running())

        activeJob = viewModelScope.launch {
            try {
                val report = runDiagnostic { stageProgress ->
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
                _uiState.value = ReportUiState(ReportStatus.Success(report))
            } catch (error: CancellationException) {
                if (!cancellationRequested) throw error
            } catch (error: Exception) {
                _uiState.value = ReportUiState(
                    ReportStatus.Error(
                        message = error.message?.takeIf { it.isNotBlank() } ?: "检测流程不可用。",
                    ),
                )
            }
        }
    }

    fun stopCheck() {
        if (_uiState.value.status !is ReportStatus.Running) return

        cancellationRequested = true
        activeJob?.cancel()
        activeJob = null
        _uiState.value = ReportUiState(ReportStatus.Cancelled)
    }
}

val diagnosticStages: List<DiagnosticStage> = listOf(
    DiagnosticStage.NETWORK_CONTEXT,
    DiagnosticStage.GATEWAY,
    DiagnosticStage.PUBLIC_CONNECTIVITY,
    DiagnosticStage.DNS,
    DiagnosticStage.DOMAIN_CONNECTIVITY,
    DiagnosticStage.ANALYSIS,
)

private fun DiagnosticStageState.toUiStatus(): ReportStageStatus = when (this) {
    DiagnosticStageState.RUNNING -> ReportStageStatus.RUNNING
    DiagnosticStageState.COMPLETED -> ReportStageStatus.COMPLETED
    DiagnosticStageState.FAILED -> ReportStageStatus.FAILED
    DiagnosticStageState.SKIPPED -> ReportStageStatus.SKIPPED
}
