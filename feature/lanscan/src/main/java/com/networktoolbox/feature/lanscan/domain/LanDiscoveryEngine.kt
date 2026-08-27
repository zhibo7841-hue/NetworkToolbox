package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanHostProbeResult
import com.networktoolbox.feature.lanscan.domain.model.LanScanRequest
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanRejectionReason
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface LanHostProbe {
    suspend fun probe(
        ipAddress: String,
        config: LanScanProbeConfig,
    ): LanHostProbeResult
}

fun interface LanScanClock {
    fun now(): Long
}

interface LanDiscoveryEngine {
    suspend fun scan(
        request: LanScanRequest,
        currentNetworkContext: suspend () -> NetworkContext,
        onUpdate: (LanScanUpdate) -> Unit = {},
    ): LanScanSession
}

class DefaultLanDiscoveryEngine(
    private val hostProbe: LanHostProbe,
    private val rangeCalculator: LanScanRangeCalculator = LanScanRangeCalculator(),
    private val clock: LanScanClock = LanScanClock { System.currentTimeMillis() },
) : LanDiscoveryEngine {
    override suspend fun scan(
        request: LanScanRequest,
        currentNetworkContext: suspend () -> NetworkContext,
        onUpdate: (LanScanUpdate) -> Unit,
    ): LanScanSession {
        val startedAt = clock.now()
        val rangeResult = rangeCalculator.calculate(request.networkContext)
        if (rangeResult is LanScanRangeResult.Rejected) {
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = when (rangeResult.reason) {
                    LanScanRejectionReason.VPN_BLOCKED ->
                        LanScanStatus.VPN_BLOCKED

                    else -> LanScanStatus.UNSUPPORTED_NETWORK
                },
                initialNetworkContext = request.networkContext,
                range = null,
                scannedHosts = 0,
                totalHosts = 0,
                discoveredDevices = emptyList(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                rejectionReason = rangeResult.reason,
                errorMessage = rangeResult.message,
            )
            onUpdate(session.toUpdate())
            return session
        }

        val range = (rangeResult as LanScanRangeResult.Ready).range
        val candidates = range.hostAddresses()
        val totalHosts = candidates.size
        val discovered = linkedMapOf<String, LanDevice>()
        val stateMutex = Mutex()
        var scannedHosts = 0

        fun currentUpdate(
            status: LanScanStatus,
            newDevice: LanDevice? = null,
            message: String? = null,
        ): LanScanUpdate = LanScanUpdate(
            status = status,
            scannedHosts = scannedHosts,
            totalHosts = totalHosts,
            discoveredDevices = discovered.values.sortedForDisplay(),
            newDevice = newDevice,
            elapsedMs = (clock.now() - startedAt).coerceAtLeast(0L),
            message = message,
        )

        suspend fun publish(outcome: HostOutcome) {
            stateMutex.withLock {
                scannedHosts += 1
                outcome.device?.let { device ->
                    discovered[device.ipAddress] = mergeDevice(discovered[device.ipAddress], device)
                }
                onUpdate(currentUpdate(LanScanStatus.SCANNING, outcome.device))
            }
        }

        try {
            onUpdate(currentUpdate(LanScanStatus.SCANNING))
            coroutineScope {
                val work = Channel<String>(capacity = Channel.UNLIMITED)
                val workerCount = minOf(request.probeConfig.maxConcurrency, candidates.size)
                val workers = List(workerCount) { index ->
                    launch(Dispatchers.Default + CoroutineName("lan-scan-worker-$index")) {
                        for (ipAddress in work) {
                            currentCoroutineContext().ensureActive()
                            ensureNetworkStable(
                                initial = request.networkContext,
                                current = currentNetworkContext(),
                            )
                            val outcome = try {
                                probeHost(
                                    ipAddress = ipAddress,
                                    context = request.networkContext,
                                    config = request.probeConfig,
                                )
                            } catch (error: NetworkChangedSignal) {
                                throw error
                            } catch (error: CancellationException) {
                                throw HostProbeCancellation(error)
                            }
                            publish(outcome)
                            ensureNetworkStable(
                                initial = request.networkContext,
                                current = currentNetworkContext(),
                            )
                        }
                    }
                }
                candidates.forEach { ipAddress -> work.trySend(ipAddress) }
                work.close()
                workers.joinAll()
            }
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = LanScanStatus.COMPLETED,
                initialNetworkContext = request.networkContext,
                range = range,
                scannedHosts = scannedHosts,
                totalHosts = totalHosts,
                discoveredDevices = discovered.values.sortedForDisplay(),
                startedAt = startedAt,
                finishedAt = finishedAt,
            )
            onUpdate(session.toUpdate())
            return session
        } catch (_: NetworkChangedSignal) {
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = LanScanStatus.NETWORK_CHANGED,
                initialNetworkContext = request.networkContext,
                range = range,
                scannedHosts = scannedHosts,
                totalHosts = totalHosts,
                discoveredDevices = discovered.values.sortedForDisplay(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = "The active network changed during the scan.",
            )
            onUpdate(session.toUpdate())
            return session
        } catch (_: HostProbeCancellation) {
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = LanScanStatus.CANCELLED,
                initialNetworkContext = request.networkContext,
                range = range,
                scannedHosts = scannedHosts,
                totalHosts = totalHosts,
                discoveredDevices = discovered.values.sortedForDisplay(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = "The scan was cancelled.",
            )
            onUpdate(session.toUpdate())
            return session
        } catch (_: CancellationException) {
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = LanScanStatus.CANCELLED,
                initialNetworkContext = request.networkContext,
                range = range,
                scannedHosts = scannedHosts,
                totalHosts = totalHosts,
                discoveredDevices = discovered.values.sortedForDisplay(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = "The scan was cancelled.",
            )
            onUpdate(session.toUpdate())
            return session
        } catch (error: Exception) {
            val finishedAt = clock.now()
            val session = LanScanSession(
                status = LanScanStatus.ERROR,
                initialNetworkContext = request.networkContext,
                range = range,
                scannedHosts = scannedHosts,
                totalHosts = totalHosts,
                discoveredDevices = discovered.values.sortedForDisplay(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = error.message ?: "LAN scan failed.",
            )
            onUpdate(session.toUpdate())
            return session
        }
    }

    private suspend fun probeHost(
        ipAddress: String,
        context: NetworkContext,
        config: LanScanProbeConfig,
    ): HostOutcome {
        val isLocalDevice = ipAddress == context.ipv4Address
        val isGateway = ipAddress == context.gateway
        if (isLocalDevice || isGateway) {
            val evidence = buildList {
                if (isLocalDevice) add(LanDiscoveryMethod.LOCAL_CONTEXT)
                if (isGateway) add(LanDiscoveryMethod.GATEWAY_CONTEXT)
            }.map { method -> LanDeviceEvidence(method = method) }
            return HostOutcome(
                device = LanDevice(
                    ipAddress = ipAddress,
                    isLocalDevice = isLocalDevice,
                    isGateway = isGateway,
                    latencyMs = null,
                    discoveryMethods = evidence.map(LanDeviceEvidence::method),
                    discoveryEvidence = evidence,
                    lastSeen = clock.now(),
                ),
            )
        }

        val probeResult = hostProbe.probe(ipAddress, config)
        if (!probeResult.hasPositiveEvidence) return HostOutcome(device = null)
        val evidence = probeResult.evidence
        return HostOutcome(
            device = LanDevice(
                ipAddress = ipAddress,
                isLocalDevice = false,
                isGateway = false,
                latencyMs = probeResult.latencyMs,
                discoveryMethods = evidence.map(LanDeviceEvidence::method).distinct(),
                discoveryEvidence = evidence,
                lastSeen = clock.now(),
            ),
        )
    }

    private fun ensureNetworkStable(
        initial: NetworkContext,
        current: NetworkContext,
    ) {
        if (initial.identity() != current.identity()) {
            throw NetworkChangedSignal()
        }
    }

    private fun mergeDevice(existing: LanDevice?, incoming: LanDevice): LanDevice {
        if (existing == null) return incoming
        val mergedEvidence = (existing.discoveryEvidence + incoming.discoveryEvidence)
            .distinctBy { evidence ->
                listOf(evidence.method, evidence.successfulPort, evidence.latencyMs, evidence.detail)
            }
        return existing.copy(
            isLocalDevice = existing.isLocalDevice || incoming.isLocalDevice,
            isGateway = existing.isGateway || incoming.isGateway,
            latencyMs = existing.latencyMs ?: incoming.latencyMs,
            discoveryMethods = mergedEvidence.map(LanDeviceEvidence::method).distinct(),
            discoveryEvidence = mergedEvidence,
            lastSeen = maxOf(existing.lastSeen, incoming.lastSeen),
        )
    }

    private fun LanScanSession.toUpdate(): LanScanUpdate = LanScanUpdate(
        status = status,
        scannedHosts = scannedHosts,
        totalHosts = totalHosts,
        discoveredDevices = discoveredDevices,
        elapsedMs = elapsedMs,
        message = errorMessage,
    )

    private data class HostOutcome(val device: LanDevice?)

    private class NetworkChangedSignal : Exception(
        "The active network changed during the scan.",
    )

    private class HostProbeCancellation(cause: CancellationException) : Exception(cause)
}

private data class NetworkIdentity(
    val activeNetworkAvailable: Boolean?,
    val connectionType: ConnectionType,
    val ipv4Address: String?,
    val ipv4PrefixLength: Int?,
    val gateway: String?,
    val interfaceName: String?,
    val vpnActive: Boolean?,
)

private fun NetworkContext.identity(): NetworkIdentity = NetworkIdentity(
    activeNetworkAvailable = activeNetworkAvailable,
    connectionType = connectionType,
    ipv4Address = ipv4Address,
    ipv4PrefixLength = ipv4PrefixLength,
    gateway = gateway,
    interfaceName = interfaceName,
    vpnActive = vpnActive,
)

private fun Collection<LanDevice>.sortedForDisplay(): List<LanDevice> =
    sortedWith(
        compareBy<LanDevice> {
            when {
                it.isGateway -> 0
                it.isLocalDevice -> 1
                else -> 2
            }
        }.thenBy { it.ipAddress.toIpv4Number() },
    )

private fun String.toIpv4Number(): Long = split('.').fold(0L) { result, part ->
    (result shl 8) or part.toLong()
}
