package com.networktoolbox.core.network.data.traceroute

import com.networktoolbox.core.network.traceroute.NativeProbeOutcome
import com.networktoolbox.core.network.traceroute.NativeSocketOpenResult

internal class NativeUdpTracerouteJni {
    external fun open(): NativeSocketOpenResult

    external fun probe(
        socketFd: Int,
        cancelReadFd: Int,
        destinationAddress: String,
        ttl: Int,
        destinationPort: Int,
        timeoutMs: Int,
    ): NativeProbeOutcome

    external fun cancel(cancelWriteFd: Int)

    external fun close(socketFd: Int, cancelReadFd: Int, cancelWriteFd: Int)

    private companion object {
        init {
            System.loadLibrary("networktoolbox_traceroute")
        }
    }
}
