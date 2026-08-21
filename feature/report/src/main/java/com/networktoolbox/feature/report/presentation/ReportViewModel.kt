package com.networktoolbox.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.feature.report.diagnostic.DiagnosticReport
import com.networktoolbox.feature.report.domain.GenerateDiagnosticReportUseCase
import com.networktoolbox.feature.report.domain.ReportStep
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
        val report: DiagnosticReport,
    ) : ReportStatus

    data class Error(
        val message: String,
    ) : ReportStatus
}

data class ReportProgress(
    val activeStep: ReportStep? = null,
    val completedSteps: Set<ReportStep> = emptySet(),
) {
    fun moveTo(step: ReportStep): ReportProgress {
        val completed = activeStep?.let { completedSteps + it } ?: completedSteps
        return copy(
            activeStep = step,
            completedSteps = completed,
        )
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val generateReport: GenerateDiagnosticReportUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun runCheck() {
        if (_uiState.value.status is ReportStatus.Running) return

        _uiState.value = ReportUiState(
            status = ReportStatus.Running(
                progress = ReportProgress(activeStep = ReportStep.NETWORK_INFORMATION),
            ),
        )

        viewModelScope.launch {
            try {
                val report = generateReport { step ->
                    _uiState.update { state ->
                        val running = state.status as? ReportStatus.Running
                            ?: return@update state
                        state.copy(
                            status = running.copy(
                                progress = running.progress.moveTo(step),
                            ),
                        )
                    }
                }
                _uiState.value = ReportUiState(ReportStatus.Success(report))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = ReportUiState(
                    ReportStatus.Error(
                        message = error.message?.takeIf { it.isNotBlank() } ?: "检测流程不可用。",
                    ),
                )
            }
        }
    }
}
