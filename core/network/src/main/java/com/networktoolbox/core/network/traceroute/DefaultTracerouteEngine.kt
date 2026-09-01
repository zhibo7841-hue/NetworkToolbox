package com.networktoolbox.core.network.traceroute

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DefaultTracerouteEngine(
    private val networkProvider: TracerouteNetworkProvider,
    private val nativeProbe: UdpTracerouteNativeProbe,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TracerouteEngine {
    override suspend fun run(request: TracerouteRequest): TracerouteResult =
        run(request, onProgress = {})

    override suspend fun run(
        request: TracerouteRequest,
        onProgress: suspend (TracerouteProgress) -> Unit,
    ): TracerouteResult = try {
        withContext(dispatcher) {
            runInternal(request, onProgress)
        }
    } catch (_: CancellationException) {
        // A cancelled dispatcher context can rethrow after runInternal has
        // already performed native cleanup. Preserve the engine contract by
        // returning a structured cancellation result at the outer boundary.
        TracerouteResult.cancelled(request)
    }

    private suspend fun runInternal(
        request: TracerouteRequest,
        onProgress: suspend (TracerouteProgress) -> Unit,
    ): TracerouteResult {
        val startedAt = now()
        TracerouteValidation.validate(request)?.let { message ->
            return TracerouteResult.failed(request, message, durationMs = elapsed(startedAt))
        }

        val network = networkProvider.current()
            ?: return TracerouteResult.failed(
                request = request,
                message = "No active network is available.",
                durationMs = elapsed(startedAt),
            )

        val resolution = try {
            resolveTarget(request, network)
        } catch (error: CancellationException) {
            return cancelledResult(request, startedAt, network.fingerprint)
        } catch (error: Exception) {
            return TracerouteResult.failed(
                request = request,
                message = error.message ?: "Target resolution failed.",
                durationMs = elapsed(startedAt),
                networkFingerprint = network.fingerprint,
            )
        }

        if (!isSameNetwork(network)) {
            return networkChangedResult(request, resolution, startedAt)
        }

        val opened = nativeProbe.open()
        if (!opened.success) {
            return TracerouteResult.failed(
                request = request,
                message = nativeFailureMessage(opened.operation, opened.errno),
                durationMs = elapsed(startedAt),
                networkFingerprint = network.fingerprint,
            )
        }

        val socket = opened.handle()
        val cancellationRegistration = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { nativeProbe.cancel(socket) }
        val hops = mutableListOf<TracerouteHop>()
        var reached = false

        try {
            val binding = network.bindSocket(socket.socketFd)
            if (!binding.success) {
                return TracerouteResult(
                    targetInput = request.target,
                    resolvedAddress = resolution.address,
                    addressFamily = request.addressFamily,
                    hops = hops.toList(),
                    status = TracerouteStatus.FAILED,
                    durationMs = elapsed(startedAt),
                    networkFingerprint = network.fingerprint,
                    fakeIpDetected = resolution.fakeIpDetected,
                    errorMessage = nativeFailureMessage(binding.operation, binding.errno),
                )
            }

            outer@ for (hopNumber in 1..request.maxHops) {
                currentCoroutineContext().ensureActive()
                if (!isSameNetwork(network)) {
                    return networkChangedResult(request, resolution, startedAt, hops, network)
                }

                val probes = mutableListOf<TracerouteProbeResult>()
                for (probeNumber in 0 until request.probesPerHop) {
                    currentCoroutineContext().ensureActive()
                    if (!isSameNetwork(network)) {
                        return networkChangedResult(request, resolution, startedAt, hops, network)
                    }

                    val port = request.destinationPort +
                        ((hopNumber - 1) * request.probesPerHop) + probeNumber
                    val nativeOutcome = nativeProbe.probe(
                        socket = socket,
                        destinationAddress = resolution.address,
                        ttl = hopNumber,
                        destinationPort = port,
                        timeoutMs = request.timeoutMs,
                    )
                    val result = NativeTracerouteOutcomeMapper.map(nativeOutcome)
                    if (result.status == TracerouteProbeStatus.CANCELLED) {
                        return cancelledResult(request, startedAt, network.fingerprint, hops)
                    }
                    if (result.status in FATAL_PROBE_STATUSES) {
                        return TracerouteResult(
                            targetInput = request.target,
                            resolvedAddress = resolution.address,
                            addressFamily = request.addressFamily,
                            hops = hops.toList(),
                            status = TracerouteStatus.FAILED,
                            durationMs = elapsed(startedAt),
                            networkFingerprint = network.fingerprint,
                            fakeIpDetected = resolution.fakeIpDetected,
                            errorMessage = nativeFailureMessage(result.operation, result.nativeError),
                        )
                    }
                    probes += result
                    if (result.status == TracerouteProbeStatus.DESTINATION_REACHED) {
                        reached = true
                        break
                    }
                }

                val hop = TracerouteHop(
                    hopNumber = hopNumber,
                    address = probes.firstNotNullOfOrNull(TracerouteProbeResult::responderAddress),
                    probes = probes.toList(),
                    status = when {
                        probes.any { it.status == TracerouteProbeStatus.DESTINATION_REACHED } ->
                            TracerouteHopStatus.DESTINATION_REACHED

                        probes.any { it.status == TracerouteProbeStatus.HOP } ->
                            TracerouteHopStatus.RESPONDED

                        else -> TracerouteHopStatus.TIMEOUT
                    },
                )
                hops += hop
                onProgress(
                    TracerouteProgress(
                        targetInput = request.target,
                        resolvedAddress = resolution.address,
                        hop = hop,
                        elapsedMs = elapsed(startedAt),
                    ),
                )
                if (reached) break@outer
            }

            return TracerouteResult(
                targetInput = request.target,
                resolvedAddress = resolution.address,
                addressFamily = request.addressFamily,
                hops = hops.toList(),
                status = if (reached) TracerouteStatus.REACHED else TracerouteStatus.PARTIAL,
                durationMs = elapsed(startedAt),
                networkFingerprint = network.fingerprint,
                fakeIpDetected = resolution.fakeIpDetected,
            )
        } catch (error: CancellationException) {
            return cancelledResult(request, startedAt, network.fingerprint, hops)
        } finally {
            cancellationRegistration?.dispose()
            nativeProbe.cancel(socket)
            nativeProbe.close(socket)
        }
    }

    private suspend fun resolveTarget(
        request: TracerouteRequest,
        network: TracerouteNetwork,
    ): TracerouteResolution {
        val target = request.target.trim()
        val literal = TracerouteValidation.normalizeIpv4Literal(target)
        if (literal != null) {
            return TracerouteResolution(
                address = literal,
                fakeIpDetected = TracerouteFakeIpDetector.isFakeIp(literal),
            )
        }
        if (!TracerouteValidation.isValidHostname(target)) {
            throw IllegalArgumentException("Invalid IPv4 address or hostname.")
        }
        val addresses = network.resolveIpv4(target)
            .mapNotNull(TracerouteValidation::normalizeIpv4Literal)
            .distinct()
        val address = addresses.firstOrNull()
            ?: throw IllegalArgumentException("No IPv4 address was resolved for the target.")
        return TracerouteResolution(
            address = address,
            fakeIpDetected = addresses.any(TracerouteFakeIpDetector::isFakeIp),
        )
    }

    private fun isSameNetwork(initial: TracerouteNetwork): Boolean =
        networkProvider.current()?.fingerprint == initial.fingerprint

    private fun networkChangedResult(
        request: TracerouteRequest,
        resolution: TracerouteResolution,
        startedAt: Long,
        hops: List<TracerouteHop> = emptyList(),
        network: TracerouteNetwork? = networkProvider.current(),
    ): TracerouteResult = TracerouteResult(
        targetInput = request.target,
        resolvedAddress = resolution.address,
        addressFamily = request.addressFamily,
        hops = hops.toList(),
        status = TracerouteStatus.NETWORK_CHANGED,
        durationMs = elapsed(startedAt),
        networkFingerprint = network?.fingerprint,
        fakeIpDetected = resolution.fakeIpDetected,
        errorMessage = "The active network changed during traceroute.",
    )

    private fun cancelledResult(
        request: TracerouteRequest,
        startedAt: Long,
        fingerprint: String?,
        hops: List<TracerouteHop> = emptyList(),
    ): TracerouteResult = TracerouteResult(
        targetInput = request.target,
        resolvedAddress = null,
        addressFamily = request.addressFamily,
        hops = hops.toList(),
        status = TracerouteStatus.CANCELLED,
        durationMs = elapsed(startedAt),
        networkFingerprint = fingerprint,
        errorMessage = "Traceroute was cancelled.",
    )

    private fun nativeFailureMessage(operation: String?, errno: Int?): String = buildString {
        append("Traceroute operation failed")
        operation?.let { append(" at ").append(it) }
        errno?.let { append(" (errno ").append(it).append(')') }
        append('.')
    }

    private fun elapsed(startedAt: Long): Long = (now() - startedAt).coerceAtLeast(0L)

    private companion object {
        val FATAL_PROBE_STATUSES = setOf(
            TracerouteProbeStatus.LOCAL_ERROR,
            TracerouteProbeStatus.PERMISSION_DENIED,
            TracerouteProbeStatus.UNSUPPORTED,
            TracerouteProbeStatus.INVALID_RESPONSE,
            TracerouteProbeStatus.NETWORK_BIND_FAILED,
        )
    }
}
