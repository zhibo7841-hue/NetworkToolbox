package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRange
import com.networktoolbox.feature.lanscan.domain.model.LanScanRequest
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

fun interface RunLanScan {
    suspend operator fun invoke(
        probeConfig: LanScanProbeConfig,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession

    suspend fun invokeWithRange(
        range: LanScanRange,
        probeConfig: LanScanProbeConfig,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession = invoke(probeConfig, onUpdate)
}

suspend operator fun RunLanScan.invoke(): LanScanSession = invoke(
    probeConfig = LanScanProbeConfig(),
    onUpdate = {},
)

class RunLanScanUseCase(
    private val networkRepository: NetworkRepository,
    private val discoveryEngine: LanDiscoveryEngine,
    private val historyRecorder: HistoryRecorder,
) : RunLanScan {
    override
    suspend operator fun invoke(
        probeConfig: LanScanProbeConfig,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession = execute(
        requestedRange = null,
        probeConfig = probeConfig,
        onUpdate = onUpdate,
    )

    override
    suspend fun invokeWithRange(
        range: LanScanRange,
        probeConfig: LanScanProbeConfig,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession = execute(
        requestedRange = range,
        probeConfig = probeConfig,
        onUpdate = onUpdate,
    )

    private suspend fun execute(
        requestedRange: LanScanRange?,
        probeConfig: LanScanProbeConfig,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession = coroutineScope {
        val initialContext = networkRepository.observeNetworkContext().first()
        val latestContext = MutableStateFlow<NetworkContext>(initialContext)
        val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
            networkRepository.observeNetworkContext().collect { context ->
                latestContext.value = context
            }
        }
        try {
            discoveryEngine.scan(
                request = LanScanRequest(
                    networkContext = initialContext,
                    probeConfig = probeConfig,
                    requestedRange = requestedRange,
                ),
                currentNetworkContext = { latestContext.value },
                onUpdate = onUpdate,
            ).also { session ->
                if (session.status == LanScanStatus.COMPLETED) {
                    historyRecorder.record(LanScanHistorySerializer.toHistoryRecord(session))
                }
            }
        } finally {
            monitor.cancel()
        }
    }
}
