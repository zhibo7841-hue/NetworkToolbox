package com.networktoolbox.core.network.ping

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PingSessionEngineTest {
    @Test
    fun singleModeExecutesExactlyOneProbe() = runBlocking {
        val calls = AtomicInteger(0)
        var receivedProtocol: PingProtocol? = null
        val engine = DefaultPingSessionEngine(
            probe = PingProbe { target, protocol, _ ->
                calls.incrementAndGet()
                receivedProtocol = protocol
                successfulAttempt(target, protocol)
            },
            clock = incrementingClock(),
        )

        val result = engine.run(
            PingRequest(
                target = "192.0.2.10",
                protocol = PingProtocol.IPV4,
                mode = PingMode.SINGLE,
                count = 1,
                intervalMs = 0,
            ),
        )

        assertEquals(1, calls.get())
        assertEquals(PingProtocol.IPV4, receivedProtocol)
        assertEquals(1, result.sentPackets)
        assertEquals(PingProtocol.IPV4, result.protocol)
        assertEquals(PingMode.SINGLE, result.mode)
    }

    @Test
    fun continuousModeStopsAfterConfiguredCount() = runBlocking {
        val calls = AtomicInteger(0)
        val engine = DefaultPingSessionEngine(
            probe = PingProbe { target, protocol, _ ->
                calls.incrementAndGet()
                successfulAttempt(target, protocol)
            },
            clock = incrementingClock(),
            waitBetweenAttempts = { },
        )

        val result = engine.run(
            PingRequest(
                target = "example.com",
                protocol = PingProtocol.AUTO,
                mode = PingMode.CONTINUOUS,
                count = 3,
                intervalMs = 1,
            ),
        )

        assertEquals(3, calls.get())
        assertEquals(3, result.sentPackets)
    }

    @Test
    fun continuousModeCanBeCancelledSafely() = runBlocking {
        val firstProbeCompleted = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val engine = DefaultPingSessionEngine(
            probe = PingProbe { target, protocol, _ ->
                calls.incrementAndGet()
                firstProbeCompleted.complete(Unit)
                successfulAttempt(target, protocol)
            },
            waitBetweenAttempts = { yield() },
        )

        val job = launch {
            engine.run(
                PingRequest(
                    target = "example.com",
                    mode = PingMode.CONTINUOUS,
                    count = null,
                    intervalMs = 1,
                ),
            )
        }
        firstProbeCompleted.await()

        job.cancel()
        job.join()

        assertTrue(calls.get() >= 1)
        assertTrue(job.isCancelled)
    }

    private fun incrementingClock(): PingClock {
        var now = 1_000L
        return PingClock { now += 1L; now }
    }

    private fun successfulAttempt(
        target: String,
        protocol: PingProtocol,
    ): PingAttemptResult = PingAttemptResult(
        target = target,
        address = "192.0.2.10",
        protocol = protocol,
        success = true,
        latencyMs = 20L,
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = null,
    )
}
