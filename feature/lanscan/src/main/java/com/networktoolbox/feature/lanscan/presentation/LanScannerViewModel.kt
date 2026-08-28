package com.networktoolbox.feature.lanscan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class LanScannerViewModel @Inject constructor(
    observeReadiness: ObserveLanScanReadiness,
    private val runScan: RunLanScan,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LanScannerUiState>(LanScannerUiState.Idle)
    val uiState: StateFlow<LanScannerUiState> = _uiState.asStateFlow()

    private var latestReadiness: LanScanReadiness? = null
    private var scanJob: Job? = null
    private val stopRequested = AtomicBoolean(false)
    private val customRangeCalculator = LanCustomRangeCalculator()
    private var rangeMode = LanScanRangeMode.CURRENT_NETWORK
    private var customRangeInitialized = false
    private var customStartAddress = ""
    private var customEndAddress = ""
    private var customRangeResult: LanCustomRangeResult = LanCustomRangeResult.Incomplete
    private var lastScanRange: LanScanRange? = null

    init {
        viewModelScope.launch {
            observeReadiness().collect { readiness ->
                latestReadiness = readiness
                if (_uiState.value.canRefreshReadiness()) {
                    _uiState.value = readiness.toUiState()
                }
            }
        }
    }

    fun startScan() {
        beginScan(requestedRange = null)
    }

    fun rescan() {
        beginScan(requestedRange = lastScanRange)
    }

    fun modifyRange() {
        if (scanJob?.isActive == true) return

        val readiness = latestReadiness
        if (readiness == null) {
            _uiState.value = LanScannerUiState.Error(
                message = "暂时无法读取当前网络状态，请稍后重试。",
            )
            return
        }
        _uiState.value = readiness.toUiState()
    }

    private fun beginScan(requestedRange: LanScanRange?) {
        if (scanJob?.isActive == true) return

        val readiness = latestReadiness
        if (readiness == null) {
            _uiState.value = LanScannerUiState.Error(
                message = "暂时无法读取当前网络状态，请稍后重试。",
            )
            return
        }
        val range = requestedRange ?: when (rangeMode) {
            LanScanRangeMode.CURRENT_NETWORK ->
                (readiness.rangeResult as? LanScanRangeResult.Ready)?.range

            LanScanRangeMode.CUSTOM ->
                (customRangeResult as? LanCustomRangeResult.Valid)?.range
        }
        if (range == null) {
            _uiState.value = readiness.toUiState()
            return
        }

        stopRequested.set(false)
        val initialUpdate = LanScanUpdate(
            status = LanScanStatus.SCANNING,
            scannedHosts = 0,
            totalHosts = range.hostCount,
            discoveredDevices = emptyList(),
            elapsedMs = 0,
        )
        _uiState.value = LanScannerUiState.Scanning(
            networkContext = readiness.networkContext,
            range = range,
            startedAt = System.currentTimeMillis(),
            update = initialUpdate,
        )
        lastScanRange = range

        scanJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                val session = if (requestedRange != null || rangeMode == LanScanRangeMode.CUSTOM) {
                    runScan.invokeWithRange(
                        range = range,
                        probeConfig = LanScanProbeConfig(),
                        onUpdate = ::publishScanUpdate,
                    )
                } else {
                    runScan(
                        probeConfig = LanScanProbeConfig(),
                        onUpdate = ::publishScanUpdate,
                    )
                }
                lastScanRange = session.range ?: range
                if (!stopRequested.get() && _uiState.value is LanScannerUiState.Scanning) {
                    _uiState.value = session.toUiState()
                }
            } catch (error: CancellationException) {
                if (!stopRequested.get()) throw error
            } catch (error: Exception) {
                if (!stopRequested.get()) {
                    _uiState.value = LanScannerUiState.Error(
                        message = error.message ?: "局域网扫描失败，请稍后重试。",
                        readiness = latestReadiness,
                    )
                }
            } finally {
                if (scanJob === currentJob) scanJob = null
            }
        }
    }

    fun selectRangeMode(mode: LanScanRangeMode) {
        if (scanJob?.isActive == true) return

        if (mode == LanScanRangeMode.CUSTOM && !customRangeInitialized) {
            val automaticRange = (latestReadiness?.rangeResult as? LanScanRangeResult.Ready)?.range
            customStartAddress = automaticRange?.firstHost.orEmpty()
            customEndAddress = automaticRange?.lastHost.orEmpty()
            customRangeResult = customRangeCalculator.calculate(
                startInput = customStartAddress,
                endInput = customEndAddress,
            )
            customRangeInitialized = true
        }
        rangeMode = mode
        publishReadinessState()
    }

    fun onCustomStartAddressChanged(value: String) {
        if (scanJob?.isActive == true) return
        customRangeInitialized = true
        customStartAddress = value
        recalculateCustomRange()
        publishReadinessState()
    }

    fun onCustomEndAddressChanged(value: String) {
        if (scanJob?.isActive == true) return
        customRangeInitialized = true
        customEndAddress = value
        recalculateCustomRange()
        publishReadinessState()
    }

    fun stopScan() {
        val current = _uiState.value as? LanScannerUiState.Scanning ?: return
        stopRequested.set(true)
        lastScanRange = current.range
        scanJob?.cancel()
        _uiState.value = LanScannerUiState.Cancelled(
            session = current.toCancelledSession(),
        )
    }

    private fun LanScanSession.toUiState(): LanScannerUiState = when (status) {
        LanScanStatus.COMPLETED -> LanScannerUiState.Completed(this)
        LanScanStatus.CANCELLED -> LanScannerUiState.Cancelled(this)
        LanScanStatus.NETWORK_CHANGED -> LanScannerUiState.NetworkChanged(this)
        LanScanStatus.VPN_BLOCKED -> LanScannerUiState.VpnBlocked(
            readiness = LanScanReadiness(
                networkContext = initialNetworkContext,
                rangeResult = LanScanRangeResult.Rejected(
                    reason = com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.VPN_BLOCKED,
                    message = errorMessage ?: "当前网络包含 VPN。",
                ),
            ),
            message = errorMessage ?: "当前网络包含 VPN。",
        )

        LanScanStatus.UNSUPPORTED_NETWORK -> LanScannerUiState.UnsupportedNetwork(
            readiness = LanScanReadiness(
                networkContext = initialNetworkContext,
                rangeResult = LanScanRangeResult.Rejected(
                    reason = com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.UNSUPPORTED_NETWORK,
                    message = errorMessage ?: "当前网络不适合局域网扫描。",
                ),
            ),
            message = errorMessage ?: "当前网络不适合局域网扫描。",
        )

        LanScanStatus.ERROR,
        LanScanStatus.IDLE,
        LanScanStatus.SCANNING,
        -> LanScannerUiState.Error(
            message = errorMessage ?: "局域网扫描失败，请稍后重试。",
            readiness = latestReadiness,
        )
    }

    private fun LanScannerUiState.Scanning.toCancelledSession(): LanScanSession =
        LanScanSession(
            status = LanScanStatus.CANCELLED,
            initialNetworkContext = networkContext,
            range = range,
            scannedHosts = update.scannedHosts,
            totalHosts = update.totalHosts,
            discoveredDevices = update.discoveredDevices,
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            errorMessage = "扫描已停止。",
        )

    private fun publishScanUpdate(update: LanScanUpdate) {
        if (!stopRequested.get() && _uiState.value is LanScannerUiState.Scanning) {
            val current = _uiState.value as LanScannerUiState.Scanning
            _uiState.value = current.copy(update = update)
        }
    }

    private fun recalculateCustomRange() {
        customRangeResult = customRangeCalculator.calculate(
            startInput = customStartAddress,
            endInput = customEndAddress,
        )
    }

    private fun publishReadinessState() {
        latestReadiness?.let { readiness ->
            _uiState.value = readiness.toUiState()
        }
    }

    private fun LanScanReadiness.toUiState(): LanScannerUiState = when (val result = rangeResult) {
        is LanScanRangeResult.Ready -> LanScannerUiState.Ready(
            readiness = this,
            range = result.range,
            rangeMode = rangeMode,
            customStartAddress = customStartAddress,
            customEndAddress = customEndAddress,
            customRangeResult = customRangeResult,
        )

        is LanScanRangeResult.Rejected -> when (result.reason) {
            com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.VPN_BLOCKED ->
                LanScannerUiState.VpnBlocked(this, result.message)

            com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason.UNSUPPORTED_NETWORK ->
                LanScannerUiState.UnsupportedNetwork(this, result.message)

            else -> LanScannerUiState.Error(result.message, this)
        }
    }
}

private fun LanScannerUiState.canRefreshReadiness(): Boolean = when (this) {
    LanScannerUiState.Idle,
    is LanScannerUiState.Ready,
    is LanScannerUiState.UnsupportedNetwork,
    is LanScannerUiState.VpnBlocked,
    is LanScannerUiState.Error,
    -> true

    is LanScannerUiState.Scanning,
    is LanScannerUiState.Completed,
    is LanScannerUiState.Cancelled,
    is LanScannerUiState.NetworkChanged,
    -> false
}
