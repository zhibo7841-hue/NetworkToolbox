package com.networktoolbox.core.network.traceroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeTracerouteOutcomeMapperTest {
    @Test
    fun mapsAllNativeStatusesWithoutExposingSentinelValues() {
        val mappings = mapOf(
            NativeTracerouteStatusCode.HOP to TracerouteProbeStatus.HOP,
            NativeTracerouteStatusCode.DESTINATION_REACHED to TracerouteProbeStatus.DESTINATION_REACHED,
            NativeTracerouteStatusCode.TIMEOUT to TracerouteProbeStatus.TIMEOUT,
            NativeTracerouteStatusCode.LOCAL_ERROR to TracerouteProbeStatus.LOCAL_ERROR,
            NativeTracerouteStatusCode.PERMISSION_DENIED to TracerouteProbeStatus.PERMISSION_DENIED,
            NativeTracerouteStatusCode.UNSUPPORTED to TracerouteProbeStatus.UNSUPPORTED,
            NativeTracerouteStatusCode.CANCELLED to TracerouteProbeStatus.CANCELLED,
            NativeTracerouteStatusCode.INVALID_RESPONSE to TracerouteProbeStatus.INVALID_RESPONSE,
        )

        mappings.forEach { (nativeCode, expectedStatus) ->
            val result = NativeTracerouteOutcomeMapper.map(
                NativeProbeOutcome(
                    statusCode = nativeCode,
                    responderAddress = "192.0.2.1",
                    latencyMs = 12,
                    icmpType = 11,
                    icmpCode = 0,
                    errno = 0,
                    operation = "TEST",
                ),
            )

            assertEquals(expectedStatus, result.status)
            assertEquals("192.0.2.1", result.responderAddress)
            assertEquals(12L, result.latencyMs)
            assertEquals(11, result.icmpType)
            assertEquals(0, result.icmpCode)
            assertEquals(0, result.nativeError)
        }

        val timeout = NativeTracerouteOutcomeMapper.map(
            NativeProbeOutcome(statusCode = NativeTracerouteStatusCode.TIMEOUT),
        )
        assertNull(timeout.latencyMs)
        assertNull(timeout.nativeError)
    }

    @Test
    fun unknownNativeStatusIsLocalError() {
        assertEquals(
            TracerouteProbeStatus.LOCAL_ERROR,
            NativeTracerouteOutcomeMapper.map(NativeProbeOutcome(statusCode = 999)).status,
        )
    }
}
