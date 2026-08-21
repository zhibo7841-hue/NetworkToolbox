package com.networktoolbox.feature.port.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.port.domain.CheckTcpPortUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TcpUiState(
    val hostInput: String = "",
    val portInput: String = "",
    val status: TcpStatus = TcpStatus.Idle,
)

sealed interface TcpStatus {
    data object Idle : TcpStatus

    data class Loading(val host: String, val port: String) : TcpStatus

    data class Success(val result: TcpProbeResult) : TcpStatus

    data class Error(val result: TcpProbeResult) : TcpStatus
}

@HiltViewModel
class TcpViewModel @Inject constructor(
    private val checkTcpPort: CheckTcpPortUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TcpUiState())
    val uiState: StateFlow<TcpUiState> = _uiState.asStateFlow()

    fun onHostChanged(host: String) {
        if (_uiState.value.status is TcpStatus.Loading) return

        _uiState.update {
            it.copy(
                hostInput = host,
                status = TcpStatus.Idle,
            )
        }
    }

    fun onPortChanged(port: String) {
        if (_uiState.value.status is TcpStatus.Loading) return

        _uiState.update {
            it.copy(
                portInput = port,
                status = TcpStatus.Idle,
            )
        }
    }

    fun check() {
        if (_uiState.value.status is TcpStatus.Loading) return

        val host = _uiState.value.hostInput.trim()
        val port = _uiState.value.portInput.trim()
        _uiState.update { it.copy(status = TcpStatus.Loading(host, port)) }

        viewModelScope.launch {
            val result = runCatching { checkTcpPort(host, port) }
                .getOrElse {
                    TcpProbeResult(
                        host = host,
                        port = port.toIntOrNull() ?: 0,
                        success = false,
                        latencyMs = null,
                        errorMessage = "Unknown error",
                    )
                }

            _uiState.update {
                it.copy(
                    status = if (result.success) {
                        TcpStatus.Success(result)
                    } else {
                        TcpStatus.Error(result)
                    },
                )
            }
        }
    }
}
