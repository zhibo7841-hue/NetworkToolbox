package com.networktoolbox.core.network.traceroute

enum class TracerouteAddressFamily {
    IPV4,
    IPV6,
}

enum class TracerouteProbeStatus {
    HOP,
    DESTINATION_REACHED,
    TIMEOUT,
    LOCAL_ERROR,
    PERMISSION_DENIED,
    UNSUPPORTED,
    CANCELLED,
    INVALID_RESPONSE,
    NETWORK_BIND_FAILED,
}

enum class TracerouteStatus {
    RUNNING,
    REACHED,
    PARTIAL,
    FAILED,
    CANCELLED,
    NETWORK_CHANGED,
}

enum class TracerouteHopStatus {
    RESPONDED,
    DESTINATION_REACHED,
    TIMEOUT,
}

data class TracerouteRequest(
    val target: String,
    val maxHops: Int = DEFAULT_MAX_HOPS,
    val probesPerHop: Int = DEFAULT_PROBES_PER_HOP,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val destinationPort: Int = DEFAULT_DESTINATION_PORT,
    val addressFamily: TracerouteAddressFamily = TracerouteAddressFamily.IPV4,
) {
    companion object {
        const val DEFAULT_MAX_HOPS = 30
        const val DEFAULT_PROBES_PER_HOP = 3
        const val DEFAULT_TIMEOUT_MS = 1_500
        const val DEFAULT_DESTINATION_PORT = 33_434
    }
}

data class TracerouteProbeResult(
    val status: TracerouteProbeStatus,
    val responderAddress: String? = null,
    val latencyMs: Long? = null,
    val icmpType: Int? = null,
    val icmpCode: Int? = null,
    val nativeError: Int? = null,
    val operation: String? = null,
)

data class TracerouteHop(
    val hopNumber: Int,
    val address: String?,
    val probes: List<TracerouteProbeResult>,
    val status: TracerouteHopStatus,
) {
    val latencies: List<Long> get() = probes.mapNotNull(TracerouteProbeResult::latencyMs)
}

data class TracerouteResult(
    val targetInput: String,
    val resolvedAddress: String?,
    val addressFamily: TracerouteAddressFamily,
    val hops: List<TracerouteHop>,
    val status: TracerouteStatus,
    val durationMs: Long?,
    val networkFingerprint: String? = null,
    val fakeIpDetected: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        fun failed(
            request: TracerouteRequest,
            message: String,
            durationMs: Long? = null,
            networkFingerprint: String? = null,
        ): TracerouteResult = TracerouteResult(
            targetInput = request.target,
            resolvedAddress = null,
            addressFamily = request.addressFamily,
            hops = emptyList(),
            status = TracerouteStatus.FAILED,
            durationMs = durationMs,
            networkFingerprint = networkFingerprint,
            errorMessage = message,
        )
    }
}

data class TracerouteResolution(
    val address: String,
    val fakeIpDetected: Boolean,
)

data class TracerouteBindResult(
    val success: Boolean,
    val operation: String? = null,
    val errno: Int? = null,
    val errorMessage: String? = null,
)

interface TracerouteNetwork {
    val fingerprint: String
    val vpnActive: Boolean?

    suspend fun resolveIpv4(hostname: String): List<String>

    fun bindSocket(socketFd: Int): TracerouteBindResult
}

fun interface TracerouteNetworkProvider {
    fun current(): TracerouteNetwork?
}
