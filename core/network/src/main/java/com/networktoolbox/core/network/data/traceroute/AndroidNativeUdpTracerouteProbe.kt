package com.networktoolbox.core.network.data.traceroute

import com.networktoolbox.core.network.traceroute.NativeProbeOutcome
import com.networktoolbox.core.network.traceroute.NativeSocketHandle
import com.networktoolbox.core.network.traceroute.NativeSocketOpenResult
import com.networktoolbox.core.network.traceroute.NativeTracerouteStatusCode
import com.networktoolbox.core.network.traceroute.UdpTracerouteNativeProbe

class AndroidNativeUdpTracerouteProbe : UdpTracerouteNativeProbe {
    private val jni: NativeUdpTracerouteJni? = runCatching {
        NativeUdpTracerouteJni()
    }.getOrNull()

    override fun open(): NativeSocketOpenResult = jni?.open()
        ?: NativeSocketOpenResult(
            socketFd = -1,
            cancelReadFd = -1,
            cancelWriteFd = -1,
            operation = "LOAD_LIBRARY",
        )

    override suspend fun probe(
        socket: NativeSocketHandle,
        destinationAddress: String,
        ttl: Int,
        destinationPort: Int,
        timeoutMs: Int,
    ): NativeProbeOutcome = jni?.probe(
        socketFd = socket.socketFd,
        cancelReadFd = socket.cancelReadFd,
        destinationAddress = destinationAddress,
        ttl = ttl,
        destinationPort = destinationPort,
        timeoutMs = timeoutMs,
    ) ?: NativeProbeOutcome(
        statusCode = NativeTracerouteStatusCode.UNSUPPORTED,
        operation = "LOAD_LIBRARY",
    )

    override fun cancel(socket: NativeSocketHandle) {
        jni?.cancel(socket.cancelWriteFd)
    }

    override fun close(socket: NativeSocketHandle) {
        jni?.close(socket.socketFd, socket.cancelReadFd, socket.cancelWriteFd)
    }
}
