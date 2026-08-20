package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.PingMethod
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPingEngineTest {
    @Test
    fun successfulReachabilityUsesSystemReachabilityMethod() = runBlocking {
        var receivedTimeout = 0
        val engine = AndroidPingEngine { address, timeoutMs ->
            assertEquals("127.0.0.1", address.hostAddress)
            receivedTimeout = timeoutMs
            true
        }

        val result = engine.ping("127.0.0.1", timeoutMs = 1_250)

        assertTrue(result.success)
        assertNotNull(result.latencyMs)
        assertEquals(PingMethod.SYSTEM_REACHABILITY, result.method)
        assertNull(result.errorMessage)
        assertEquals(1_250, receivedTimeout)
    }

    @Test
    fun failedReachabilityDoesNotReportSuccess() = runBlocking {
        val engine = AndroidPingEngine { _, _ -> false }

        val result = engine.ping("127.0.0.1")

        assertFalse(result.success)
        assertNull(result.latencyMs)
        assertEquals(PingMethod.SYSTEM_REACHABILITY, result.method)
        assertEquals("Target is not reachable.", result.errorMessage)
    }

    @Test
    fun emptyTargetReturnsUnavailableWithoutCallingProbe() = runBlocking {
        var probeCalled = false
        val engine = AndroidPingEngine { _, _ ->
            probeCalled = true
            true
        }

        val result = engine.ping(" ")

        assertFalse(result.success)
        assertEquals("", result.target)
        assertEquals(PingMethod.UNAVAILABLE, result.method)
        assertEquals("Invalid target.", result.errorMessage)
        assertFalse(probeCalled)
    }

    @Test
    fun malformedTargetReturnsUnavailableWithoutCallingProbe() = runBlocking {
        var probeCalled = false
        val engine = AndroidPingEngine { _, _ ->
            probeCalled = true
            true
        }

        val result = engine.ping("abc..123")

        assertFalse(result.success)
        assertEquals(PingMethod.UNAVAILABLE, result.method)
        assertEquals("Invalid target.", result.errorMessage)
        assertFalse(probeCalled)
    }

    @Test
    fun nonPositiveTimeoutReturnsUnavailable() = runBlocking {
        var probeCalled = false
        val engine = AndroidPingEngine { _, _ ->
            probeCalled = true
            true
        }

        val result = engine.ping("127.0.0.1", timeoutMs = 0)

        assertFalse(result.success)
        assertEquals(PingMethod.UNAVAILABLE, result.method)
        assertEquals("Timeout must be greater than zero.", result.errorMessage)
        assertFalse(probeCalled)
    }

    @Test
    fun reachabilityExceptionReturnsUnavailable() = runBlocking {
        val engine = AndroidPingEngine { _, _ ->
            throw IOException("system reachability unavailable")
        }

        val result = engine.ping("127.0.0.1")

        assertFalse(result.success)
        assertEquals(PingMethod.UNAVAILABLE, result.method)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun defaultTimeoutIsThreeSeconds() {
        assertEquals(3_000, PingEngine.DEFAULT_TIMEOUT_MS)
    }
}
