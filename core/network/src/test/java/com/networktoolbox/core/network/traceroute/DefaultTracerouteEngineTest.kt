package com.networktoolbox.core.network.traceroute

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTracerouteEngineTest {
    @Test
    fun hopResponsesAndDestinationPortUnreachableProduceReachedTrace() = runBlocking {
        val probe = FakeNativeProbe(
            NativeProbeOutcome(NativeTracerouteStatusCode.HOP, responderAddress = "192.0.2.1", latencyMs = 4),
            NativeProbeOutcome(NativeTracerouteStatusCode.HOP, responderAddress = "192.0.2.2", latencyMs = 8),
            NativeProbeOutcome(
                NativeTracerouteStatusCode.DESTINATION_REACHED,
                responderAddress = "1.1.1.1",
                latencyMs = 12,
                icmpType = 3,
                icmpCode = 3,
            ),
        )
        val engine = engineWith(probe)

        val result = engine.run(
            TracerouteRequest(target = "1.1.1.1", maxHops = 5, probesPerHop = 1),
        )

        assertEquals(TracerouteStatus.REACHED, result.status)
        assertEquals(listOf(1, 2, 3), result.hops.map(TracerouteHop::hopNumber))
        assertEquals(listOf(33_434, 33_435, 33_436), probe.calls.map { it.destinationPort })
        assertEquals("1.1.1.1", result.hops.last().address)
    }

    @Test
    fun timeoutDoesNotStopLaterHopAndMaxHopsProducesPartial() = runBlocking {
        val probe = FakeNativeProbe(
            NativeProbeOutcome(NativeTracerouteStatusCode.TIMEOUT),
            NativeProbeOutcome(NativeTracerouteStatusCode.HOP, responderAddress = "192.0.2.2", latencyMs = 9),
            NativeProbeOutcome(NativeTracerouteStatusCode.TIMEOUT),
        )
        val result = engineWith(probe).run(
            TracerouteRequest(target = "1.1.1.1", maxHops = 3, probesPerHop = 1),
        )

        assertEquals(TracerouteStatus.PARTIAL, result.status)
        assertEquals(3, result.hops.size)
        assertEquals(TracerouteHopStatus.TIMEOUT, result.hops.first().status)
        assertEquals(TracerouteHopStatus.RESPONDED, result.hops[1].status)
    }

    @Test
    fun hostnameIsResolvedOutsideNativeAndFakeIpIsFlagged() = runBlocking {
        val network = FakeNetwork(resolvedAddresses = listOf("198.18.0.10", "192.0.2.10"))
        val probe = FakeNativeProbe(
            NativeProbeOutcome(NativeTracerouteStatusCode.DESTINATION_REACHED, responderAddress = "198.18.0.10"),
        )
        val result = DefaultTracerouteEngine(
            networkProvider = FakeNetworkProvider(network),
            nativeProbe = probe,
        ).run(TracerouteRequest(target = "example.com", maxHops = 1, probesPerHop = 1))

        assertEquals("198.18.0.10", result.resolvedAddress)
        assertTrue(result.fakeIpDetected)
        assertEquals("198.18.0.10", probe.calls.single().destinationAddress)
    }

    @Test
    fun noNetworkResolutionFailureAndBindFailureAreStructuredFailures() = runBlocking {
        val noNetwork = DefaultTracerouteEngine(
            networkProvider = FakeNetworkProvider(null),
            nativeProbe = FakeNativeProbe(),
        ).run(TracerouteRequest("1.1.1.1"))
        assertEquals(TracerouteStatus.FAILED, noNetwork.status)
        assertTrue(noNetwork.errorMessage.orEmpty().contains("active network"))

        val resolutionFailure = DefaultTracerouteEngine(
            networkProvider = FakeNetworkProvider(FakeNetwork(resolvedAddresses = emptyList())),
            nativeProbe = FakeNativeProbe(),
        ).run(TracerouteRequest("example.com"))
        assertEquals(TracerouteStatus.FAILED, resolutionFailure.status)

        val bindProbe = FakeNativeProbe(
            openResult = NativeSocketOpenResult(10, 11, 12),
        )
        val bindFailure = DefaultTracerouteEngine(
            networkProvider = FakeNetworkProvider(FakeNetwork(bindResult = TracerouteBindResult(false, "BIND", 1))),
            nativeProbe = bindProbe,
        ).run(TracerouteRequest("1.1.1.1", maxHops = 1, probesPerHop = 1))
        assertEquals(TracerouteStatus.FAILED, bindFailure.status)
        assertEquals(0, bindProbe.calls.size)
        assertEquals(1, bindProbe.closeCalls.get())
    }

    @Test
    fun networkChangeStopsTraceBeforeMixingResults() = runBlocking {
        val first = FakeNetwork(fingerprint = "wifi")
        val second = FakeNetwork(fingerprint = "cellular")
        val provider = SwitchingNetworkProvider(first, second, changeOnCall = 5)
        val probe = FakeNativeProbe(
            NativeProbeOutcome(NativeTracerouteStatusCode.HOP, responderAddress = "192.0.2.1"),
        )
        val result = DefaultTracerouteEngine(provider, probe).run(
            TracerouteRequest("1.1.1.1", maxHops = 3, probesPerHop = 1),
        )

        assertEquals(TracerouteStatus.NETWORK_CHANGED, result.status)
        assertEquals(1, result.hops.size)
        assertEquals(1, probe.calls.size)
    }

    @Test
    fun cancellationCancelsNativeWaitAndClosesSocket() = runBlocking {
        val probeStarted = CompletableDeferred<Unit>()
        val result = CompletableDeferred<TracerouteResult>()
        val probe = FakeNativeProbe(
            waitForCancellation = true,
            onProbeStarted = { probeStarted.complete(Unit) },
        )
        val job = launch {
            val outcome = runCatching {
                DefaultTracerouteEngine(
                    networkProvider = FakeNetworkProvider(FakeNetwork()),
                    nativeProbe = probe,
                ).run(TracerouteRequest("1.1.1.1", maxHops = 1, probesPerHop = 1))
            }.onFailure { error ->
                if (error !is CancellationException) {
                    result.completeExceptionally(error)
                }
            }
            outcome.getOrNull()?.let(result::complete)
        }

        probeStarted.await()
        job.cancelAndJoin()

        assertEquals(TracerouteStatus.CANCELLED, result.await().status)
        assertTrue(probe.cancelCalls.get() >= 1)
        assertEquals(1, probe.closeCalls.get())
    }

    private fun engineWith(probe: FakeNativeProbe): DefaultTracerouteEngine =
        DefaultTracerouteEngine(
            networkProvider = FakeNetworkProvider(FakeNetwork()),
            nativeProbe = probe,
        )

    private data class ProbeCall(
        val destinationAddress: String,
        val ttl: Int,
        val destinationPort: Int,
        val timeoutMs: Int,
    )

    private class FakeNativeProbe(
        vararg outcomes: NativeProbeOutcome,
        private val openResult: NativeSocketOpenResult = NativeSocketOpenResult(10, 11, 12),
        private val waitForCancellation: Boolean = false,
        private val onProbeStarted: () -> Unit = {},
    ) : UdpTracerouteNativeProbe {
        private val outcomes = ArrayDeque(outcomes.toList())
        val calls = mutableListOf<ProbeCall>()
        val cancelCalls = AtomicInteger(0)
        val closeCalls = AtomicInteger(0)

        override fun open(): NativeSocketOpenResult = openResult

        override suspend fun probe(
            socket: NativeSocketHandle,
            destinationAddress: String,
            ttl: Int,
            destinationPort: Int,
            timeoutMs: Int,
        ): NativeProbeOutcome {
            calls += ProbeCall(destinationAddress, ttl, destinationPort, timeoutMs)
            onProbeStarted()
            if (waitForCancellation) kotlinx.coroutines.awaitCancellation()
            return if (outcomes.isEmpty()) {
                NativeProbeOutcome(NativeTracerouteStatusCode.TIMEOUT)
            } else {
                outcomes.removeFirst()
            }
        }

        override fun cancel(socket: NativeSocketHandle) {
            cancelCalls.incrementAndGet()
        }

        override fun close(socket: NativeSocketHandle) {
            closeCalls.incrementAndGet()
        }
    }

    private open class FakeNetwork(
        override val fingerprint: String = "test-network",
        private val resolvedAddresses: List<String> = listOf("192.0.2.10"),
        private val bindResult: TracerouteBindResult = TracerouteBindResult(success = true),
    ) : TracerouteNetwork {
        override val vpnActive: Boolean? = false

        override suspend fun resolveIpv4(hostname: String): List<String> = resolvedAddresses

        override fun bindSocket(socketFd: Int): TracerouteBindResult = bindResult
    }

    private class FakeNetworkProvider(
        private var network: TracerouteNetwork?,
    ) : TracerouteNetworkProvider {
        override fun current(): TracerouteNetwork? = network
    }

    private class SwitchingNetworkProvider(
        private val first: TracerouteNetwork,
        private val second: TracerouteNetwork,
        private val changeOnCall: Int,
    ) : TracerouteNetworkProvider {
        private var calls = 0

        override fun current(): TracerouteNetwork? {
            calls++
            return if (calls >= changeOnCall) second else first
        }
    }
}
