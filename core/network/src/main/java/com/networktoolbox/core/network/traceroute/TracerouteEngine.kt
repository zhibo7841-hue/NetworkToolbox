package com.networktoolbox.core.network.traceroute

interface TracerouteEngine {
    suspend fun run(request: TracerouteRequest): TracerouteResult

    /** Reports each completed hop while preserving the one-shot contract. */
    suspend fun run(
        request: TracerouteRequest,
        onProgress: suspend (TracerouteProgress) -> Unit,
    ): TracerouteResult = run(request)
}

interface UdpTracerouteNativeProbe {
    fun open(): NativeSocketOpenResult

    suspend fun probe(
        socket: NativeSocketHandle,
        destinationAddress: String,
        ttl: Int,
        destinationPort: Int,
        timeoutMs: Int,
    ): NativeProbeOutcome

    fun cancel(socket: NativeSocketHandle)

    fun close(socket: NativeSocketHandle)
}

data class NativeSocketHandle(
    val socketFd: Int,
    val cancelReadFd: Int,
    val cancelWriteFd: Int,
)

data class NativeSocketOpenResult(
    val socketFd: Int,
    val cancelReadFd: Int,
    val cancelWriteFd: Int,
    val operation: String? = null,
    val errno: Int? = null,
) {
    val success: Boolean
        get() = socketFd >= 0 && cancelReadFd >= 0 && cancelWriteFd >= 0 && operation == null

    fun handle(): NativeSocketHandle = NativeSocketHandle(
        socketFd = socketFd,
        cancelReadFd = cancelReadFd,
        cancelWriteFd = cancelWriteFd,
    )
}

data class NativeProbeOutcome(
    val statusCode: Int,
    val responderAddress: String? = null,
    val latencyMs: Long = -1,
    val icmpType: Int = -1,
    val icmpCode: Int = -1,
    val errno: Int = -1,
    val operation: String? = null,
)

object NativeTracerouteStatusCode {
    const val HOP = 1
    const val DESTINATION_REACHED = 2
    const val TIMEOUT = 3
    const val LOCAL_ERROR = 4
    const val PERMISSION_DENIED = 5
    const val UNSUPPORTED = 6
    const val CANCELLED = 7
    const val INVALID_RESPONSE = 8
}

object NativeTracerouteOutcomeMapper {
    fun map(outcome: NativeProbeOutcome): TracerouteProbeResult = TracerouteProbeResult(
        status = when (outcome.statusCode) {
            NativeTracerouteStatusCode.HOP -> TracerouteProbeStatus.HOP
            NativeTracerouteStatusCode.DESTINATION_REACHED ->
                TracerouteProbeStatus.DESTINATION_REACHED

            NativeTracerouteStatusCode.TIMEOUT -> TracerouteProbeStatus.TIMEOUT
            NativeTracerouteStatusCode.PERMISSION_DENIED -> TracerouteProbeStatus.PERMISSION_DENIED
            NativeTracerouteStatusCode.UNSUPPORTED -> TracerouteProbeStatus.UNSUPPORTED
            NativeTracerouteStatusCode.CANCELLED -> TracerouteProbeStatus.CANCELLED
            NativeTracerouteStatusCode.INVALID_RESPONSE -> TracerouteProbeStatus.INVALID_RESPONSE
            else -> TracerouteProbeStatus.LOCAL_ERROR
        },
        responderAddress = outcome.responderAddress,
        latencyMs = outcome.latencyMs.takeIf { it >= 0 },
        icmpType = outcome.icmpType.takeIf { it >= 0 },
        icmpCode = outcome.icmpCode.takeIf { it >= 0 },
        nativeError = outcome.errno.takeIf { it >= 0 },
        operation = outcome.operation,
    )
}
