package com.networktoolbox.feature.ping.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.ping.PingRequest
import com.networktoolbox.core.network.ping.PingSessionProgress
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.feature.ping.domain.ExecutePingSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PingDetectionMode {
    QUICK,
    CONTINUOUS,
}

private const val DEFAULT_QUICK_COUNT = 5
private const val DEFAULT_QUICK_INTERVAL_MS = 500
private const val DEFAULT_CONTINUOUS_COUNT = 100
private const val DEFAULT_CONTINUOUS_INTERVAL_MS = 1_000

data class PingUiState(
    val targetInput: String = "",
    val mode: PingDetectionMode = PingDetectionMode.QUICK,
    val protocol: PingProtocol = PingProtocol.AUTO,
    val countInput: String = DEFAULT_QUICK_COUNT.toString(),
    val intervalInput: String = DEFAULT_QUICK_INTERVAL_MS.toString(),
    val status: PingStatus = PingStatus.Idle,
)

sealed interface PingStatus {
    data object Idle : PingStatus

    data class Running(
        val target: String,
        val expectedCount: Int?,
        val completedCount: Int = 0,
        val latestLatencyMs: Long? = null,
        val minLatencyMs: Long? = null,
        val avgLatencyMs: Double? = null,
        val maxLatencyMs: Long? = null,
        val packetLoss: Double = 0.0,
    ) : PingStatus

    data class Success(val result: PingSessionResult) : PingStatus

    data class Failed(val result: PingSessionResult) : PingStatus

    data class Cancelled(val target: String) : PingStatus
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val executePingSession: ExecutePingSessionUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var continuousParametersEdited = false

    fun onTargetChanged(target: String) {
        if (_uiState.value.status is PingStatus.Running) return
        _uiState.update { it.copy(targetInput = target, status = PingStatus.Idle) }
    }

    fun onModeChanged(mode: PingDetectionMode) {
        if (_uiState.value.status is PingStatus.Running) return
        _uiState.update { state ->
            val firstContinuousSelection = mode == PingDetectionMode.CONTINUOUS &&
                state.mode != PingDetectionMode.CONTINUOUS &&
                !continuousParametersEdited
            state.copy(
                mode = mode,
                countInput = if (firstContinuousSelection) {
                    DEFAULT_CONTINUOUS_COUNT.toString()
                } else {
                    state.countInput
                },
                intervalInput = if (firstContinuousSelection) {
                    DEFAULT_CONTINUOUS_INTERVAL_MS.toString()
                } else {
                    state.intervalInput
                },
                status = PingStatus.Idle,
            )
        }
    }

    fun onProtocolChanged(protocol: PingProtocol) {
        if (_uiState.value.status is PingStatus.Running) return
        _uiState.update { it.copy(protocol = protocol, status = PingStatus.Idle) }
    }

    fun onCountChanged(count: String) {
        if (_uiState.value.status is PingStatus.Running) return
        if (_uiState.value.mode == PingDetectionMode.CONTINUOUS) {
            continuousParametersEdited = true
        }
        _uiState.update { it.copy(countInput = count, status = PingStatus.Idle) }
    }

    fun onIntervalChanged(interval: String) {
        if (_uiState.value.status is PingStatus.Running) return
        if (_uiState.value.mode == PingDetectionMode.CONTINUOUS) {
            continuousParametersEdited = true
        }
        _uiState.update { it.copy(intervalInput = interval, status = PingStatus.Idle) }
    }

    fun ping() {
        startPing()
    }

    fun startPing() {
        if (_uiState.value.status is PingStatus.Running) return

        val state = _uiState.value
        val target = state.targetInput.trim()
        val request = createRequest(state, target) ?: return
        _uiState.update {
            it.copy(
                targetInput = target,
                status = PingStatus.Running(
                    target = target,
                    expectedCount = request.count,
                ),
            )
        }

        sessionJob = viewModelScope.launch {
            try {
                val result = executePingSession(
                    request = request,
                    onProgress = ::updateProgress,
                )
                _uiState.update {
                    it.copy(
                        status = if (result.receivedPackets > 0) {
                            PingStatus.Success(result)
                        } else {
                            PingStatus.Failed(result)
                        },
                    )
                }
            } catch (_: CancellationException) {
                // Stop is responsible for exposing the Cancelled state. A
                // lifecycle cancellation must not create a history record.
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(status = PingStatus.Failed(failedResult(request, "Ping unavailable.")))
                }
            } finally {
                sessionJob = null
            }
        }
    }

    private fun updateProgress(progress: PingSessionProgress) {
        _uiState.update { state ->
            val running = state.status as? PingStatus.Running ?: return@update state
            state.copy(
                status = running.copy(
                    completedCount = progress.sentPackets,
                    latestLatencyMs = progress.latestLatencyMs,
                    minLatencyMs = progress.minLatencyMs,
                    avgLatencyMs = progress.avgLatencyMs,
                    maxLatencyMs = progress.maxLatencyMs,
                    packetLoss = progress.packetLoss,
                ),
            )
        }
    }

    fun stopPing() {
        val running = _uiState.value.status as? PingStatus.Running ?: return
        sessionJob?.cancel()
        sessionJob = null
        _uiState.update { it.copy(status = PingStatus.Cancelled(running.target)) }
    }

    private fun createRequest(state: PingUiState, target: String): PingRequest? {
        if (!isValidTarget(target)) {
            setValidationError(state, target, "Invalid target.")
            return null
        }

        return when (state.mode) {
            PingDetectionMode.QUICK -> PingRequest(
                target = target,
                protocol = state.protocol,
                mode = PingMode.CONTINUOUS,
                count = DEFAULT_QUICK_COUNT,
                intervalMs = DEFAULT_QUICK_INTERVAL_MS,
            )

            PingDetectionMode.CONTINUOUS -> {
                val count = state.countInput.toIntOrNull()
                val interval = state.intervalInput.toIntOrNull()
                when {
                    count == null || count !in MIN_CONTINUOUS_COUNT..MAX_CONTINUOUS_COUNT -> {
                        setValidationError(state, target, "Invalid count.")
                        null
                    }

                    interval == null || interval !in MIN_INTERVAL_MS..MAX_INTERVAL_MS -> {
                        setValidationError(state, target, "Invalid interval.")
                        null
                    }

                    else -> PingRequest(
                        target = target,
                        protocol = state.protocol,
                        mode = PingMode.CONTINUOUS,
                        count = count,
                        intervalMs = interval,
                    )
                }
            }
        }
    }

    private fun setValidationError(
        state: PingUiState,
        target: String,
        errorMessage: String,
    ) {
        val mode = when (state.mode) {
            PingDetectionMode.QUICK -> PingMode.CONTINUOUS
            PingDetectionMode.CONTINUOUS -> PingMode.CONTINUOUS
        }
        _uiState.update {
            it.copy(
                status = PingStatus.Failed(
                    failedResult(
                        request = PingRequest(
                            target = target,
                            protocol = state.protocol,
                            mode = mode,
                            count = if (mode == PingMode.SINGLE) {
                                1
                            } else {
                                DEFAULT_CONTINUOUS_COUNT
                            },
                            intervalMs = if (mode == PingMode.SINGLE) {
                                0
                            } else {
                                DEFAULT_CONTINUOUS_INTERVAL_MS
                            },
                        ),
                        errorMessage = errorMessage,
                    ),
                ),
            )
        }
    }

    private fun isValidTarget(target: String): Boolean = target.isNotEmpty() &&
        target.none { it.isWhitespace() } &&
        !target.startsWith('.') &&
        !target.endsWith('.') &&
        !target.contains("..")

    private fun failedResult(request: PingRequest, errorMessage: String): PingSessionResult {
        val now = System.currentTimeMillis()
        return PingSessionResult(
            target = request.target,
            address = null,
            protocol = request.protocol,
            mode = request.mode,
            startTime = now,
            endTime = now,
            sentPackets = 0,
            receivedPackets = 0,
            lostPackets = 0,
            packetLoss = 0.0,
            minLatencyMs = null,
            avgLatencyMs = null,
            maxLatencyMs = null,
            jitterMs = null,
            qualityLevel = PingQualityLevel.UNKNOWN,
            summary = "无法完成 Ping 检测。",
            method = PingMethod.UNAVAILABLE,
            errorMessage = errorMessage,
        )
    }

    private companion object {
        const val MIN_CONTINUOUS_COUNT = 1
        const val MAX_CONTINUOUS_COUNT = 100
        const val MIN_INTERVAL_MS = 100
        const val MAX_INTERVAL_MS = 60_000
    }
}
