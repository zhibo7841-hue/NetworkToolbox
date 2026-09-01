package com.networktoolbox.feature.traceroute.domain

import com.networktoolbox.core.network.traceroute.TracerouteEngine
import com.networktoolbox.core.network.traceroute.TracerouteRequest
import com.networktoolbox.core.network.traceroute.TracerouteResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTracerouteUseCaseTest {
    @Test
    fun delegatesRequestToEngine() = kotlinx.coroutines.runBlocking {
        val engine = FakeEngine()
        val useCase = RunTracerouteUseCase(engine)
        val request = TracerouteRequest("1.1.1.1")

        val result = useCase(request)

        assertEquals(request, engine.lastRequest)
        assertEquals(engine.result, result)
    }

    private class FakeEngine : TracerouteEngine {
        val result = TracerouteResult.failed(TracerouteRequest("1.1.1.1"), "test")
        var lastRequest: TracerouteRequest? = null

        override suspend fun run(request: TracerouteRequest): TracerouteResult {
            lastRequest = request
            return result
        }
    }
}
