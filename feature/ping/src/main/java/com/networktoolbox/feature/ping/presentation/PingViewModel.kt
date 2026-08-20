package com.networktoolbox.feature.ping.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.feature.ping.domain.ExecutePingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PingUiState(
    val targetInput: String = "",
    val status: PingStatus = PingStatus.Idle,
)

sealed interface PingStatus {
    data object Idle : PingStatus

    data class Running(val target: String) : PingStatus

    data class Success(val result: PingResult) : PingStatus

    data class Failed(val result: PingResult) : PingStatus
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val executePing: ExecutePingUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    fun onTargetChanged(target: String) {
        if (_uiState.value.status is PingStatus.Running) return

        _uiState.update {
            it.copy(
                targetInput = target,
                status = PingStatus.Idle,
            )
        }
    }

    fun ping() {
        if (_uiState.value.status is PingStatus.Running) return

        val target = _uiState.value.targetInput.trim()
        _uiState.update { it.copy(status = PingStatus.Running(target)) }

        viewModelScope.launch {
            val result = runCatching { executePing(target) }
                .getOrElse {
                    PingResult(
                        target = target,
                        success = false,
                        latencyMs = null,
                        method = PingMethod.UNAVAILABLE,
                        errorMessage = "Ping unavailable.",
                    )
                }

            _uiState.update {
                it.copy(
                    status = if (result.success) {
                        PingStatus.Success(result)
                    } else {
                        PingStatus.Failed(result)
                    },
                )
            }
        }
    }
}
