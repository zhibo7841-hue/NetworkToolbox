package com.networktoolbox.feature.lanscan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanCustomRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.MdnsDeviceEnrichment
import com.networktoolbox.feature.lanscan.domain.MdnsEnricher
import com.networktoolbox.feature.lanscan.domain.NoOpUpnpEnricher
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentResult
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnrichmentStatus
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.UpnpDeviceEnrichment
import com.networktoolbox.feature.lanscan.domain.UpnpEnricher
import com.networktoolbox.feature.lanscan.domain.upnpNetworkIdentity
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import com.networktoolbox.feature.lanscan.domain.toLanMdnsObservation
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
    private val reverseDnsEnricher: ReverseDnsEnricher,
    private val mdnsEnricher: MdnsEnricher,
    private val upnpEnricher: UpnpEnricher = NoOpUpnpEnricher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LanScannerUiState>(LanScannerUiState.Idle)
    val uiState: StateFlow<LanScannerUiState> = _uiState.asStateFlow()

    private var latestReadiness: LanScanReadiness? = null
    private var scanJob: Job? = null
    private var enrichmentJob: Job? = null
    private var mdnsJob: Job? = null
    private var upnpJob: Job? = null
    private var enrichmentGeneration: Long = 0L
    private var enrichmentNetworkContext: NetworkContext? = null
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
                if (enrichmentNetworkContext?.isSameLanScanNetworkAs(readiness.networkContext) == false) {
                    invalidateEnrichment()
                }
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

        invalidateEnrichment()

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

        val generation = beginNewEnrichmentGeneration()
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
                    if (session.status == LanScanStatus.COMPLETED) {
                        startPostDiscoveryEnrichment(session, generation)
                    }
                }
            } catch (error: CancellationException) {
                if (!stopRequested.get()) throw error
            } catch (_: Exception) {
                if (!stopRequested.get()) {
                    _uiState.value = LanScannerUiState.Error(
                        message = "局域网扫描失败，请稍后重试。",
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
        val current = _uiState.value as? LanScannerUiState.Scanning
        if (current == null) {
            // This method is also called when leaving the screen. A completed
            // scan may still have active post-discovery enrichment to stop.
            invalidateEnrichment()
            return
        }
        stopRequested.set(true)
        invalidateEnrichment()
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
            message = "局域网扫描失败，请稍后重试。",
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

    private fun startPostDiscoveryEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        enrichmentNetworkContext = session.initialNetworkContext
        startReverseDnsEnrichment(session, generation)
        startMdnsEnrichment(session, generation)
        startUpnpEnrichment(session, generation)
    }

    private fun startReverseDnsEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        enrichmentJob = viewModelScope.launch {
            reverseDnsEnricher.enrich(session.discoveredDevices) { result ->
                if (generation == enrichmentGeneration) {
                    applyReverseDnsResult(result)
                }
            }
        }
    }

    private fun startMdnsEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        mdnsJob = viewModelScope.launch {
            mdnsEnricher.enrich(
                devices = session.discoveredDevices,
                networkContext = session.initialNetworkContext,
                generation = generation,
            ) { result ->
                if (
                    generation == enrichmentGeneration &&
                        result.observation.generation == generation &&
                        result.observation.networkIdentity == session.initialNetworkContext.mdnsIdentityForUi()
                ) {
                    applyMdnsResult(result)
                }
            }
        }
    }

    private fun applyReverseDnsResult(result: ReverseDnsEnrichmentResult) {
        if (result.status != ReverseDnsEnrichmentStatus.RESOLVED || result.hostname.isNullOrBlank()) {
            return
        }
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress == result.ipAddress) {
                device.copy(
                    hostName = result.hostname,
                    hostNameSource = result.source,
                )
            } else {
                device
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
    }

    private fun applyMdnsResult(result: MdnsDeviceEnrichment) {
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val observation = result.observation.toLanMdnsObservation()
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress != result.ipAddress) {
                device
            } else {
                val observations = (device.mdnsObservations
                    .filterNot { it.identityKey == observation.identityKey } + observation)
                    .takeLast(MAX_MDNS_OBSERVATIONS_PER_DEVICE)
                device.copy(
                    // Reverse DNS remains the primary existing hostName. mDNS
                    // contributes a candidate without overwriting that result.
                    mdnsDisplayNameCandidate = device.mdnsDisplayNameCandidate
                        ?: result.mdnsDisplayNameCandidate,
                    mdnsObservations = observations,
                )
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
    }

    private fun startUpnpEnrichment(
        session: LanScanSession,
        generation: Long,
    ) {
        upnpJob = viewModelScope.launch {
            upnpEnricher.enrich(
                devices = session.discoveredDevices,
                networkContext = session.initialNetworkContext,
                generation = generation,
            ) { result ->
                if (
                    generation == enrichmentGeneration &&
                        result.observation.source ==
                        com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource.UPNP &&
                        result.observation.generation == generation &&
                        result.observation.networkIdentity ==
                        session.initialNetworkContext.upnpNetworkIdentity()
                ) {
                    applyUpnpResult(result)
                }
            }
        }
    }

    private fun applyUpnpResult(result: UpnpDeviceEnrichment) {
        val state = _uiState.value as? LanScannerUiState.Completed ?: return
        val updatedDevices = state.session.discoveredDevices.map { device ->
            if (device.ipAddress != result.ipAddress) {
                device
            } else {
                val observations = (
                    device.upnpObservations.filterNot { observation ->
                        observation.udn == result.observation.udn &&
                            observation.usn == result.observation.usn
                    } + result.observation
                    ).takeLast(MAX_UPNP_OBSERVATIONS_PER_DEVICE)
                device.copy(
                    // UPnP is an additional candidate. Existing reverse DNS and
                    // mDNS values are never overwritten by this enrichment.
                    upnpDisplayNameCandidate = device.upnpDisplayNameCandidate
                        ?: result.upnpDisplayNameCandidate,
                    upnpObservations = observations,
                )
            }
        }
        _uiState.value = state.copy(
            session = state.session.copy(discoveredDevices = updatedDevices),
        )
    }

    private fun beginNewEnrichmentGeneration(): Long {
        invalidateEnrichment()
        return enrichmentGeneration
    }

    private fun invalidateEnrichment() {
        enrichmentGeneration += 1L
        enrichmentNetworkContext = null
        enrichmentJob?.cancel()
        enrichmentJob = null
        mdnsJob?.cancel()
        mdnsJob = null
        upnpJob?.cancel()
        upnpJob = null
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

    override fun onCleared() {
        invalidateEnrichment()
        super.onCleared()
    }
}

private fun NetworkContext.isSameLanScanNetworkAs(
    other: NetworkContext,
): Boolean =
    activeNetworkAvailable == other.activeNetworkAvailable &&
        connectionType == other.connectionType &&
        ipv4Address == other.ipv4Address &&
        ipv4PrefixLength == other.ipv4PrefixLength &&
        gateway == other.gateway &&
        interfaceName == other.interfaceName &&
        vpnActive == other.vpnActive

private fun NetworkContext.mdnsIdentityForUi(): String = listOf(
    connectionType.name,
    interfaceName.orEmpty(),
    ipv4Address.orEmpty(),
    ipv4PrefixLength?.toString().orEmpty(),
    gateway.orEmpty(),
).joinToString(separator = "|")

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

private const val MAX_MDNS_OBSERVATIONS_PER_DEVICE = 16
private const val MAX_UPNP_OBSERVATIONS_PER_DEVICE = 8
