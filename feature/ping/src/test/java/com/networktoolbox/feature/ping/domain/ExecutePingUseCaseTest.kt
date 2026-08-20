package com.networktoolbox.feature.ping.domain

import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.feature.ping.FakePingEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutePingUseCaseTest {
    @Test
    fun delegatesTargetAndTimeoutToEngine() = runTest {
        val expected = PingResult(
            target = "127.0.0.1",
            success = true,
            latencyMs = 1L,
            method = PingMethod.SYSTEM_REACHABILITY,
            errorMessage = null,
        )
        val engine = FakePingEngine(expected)
        val useCase = ExecutePingUseCase(engine)

        val result = useCase("127.0.0.1", timeoutMs = 1_500)

        assertEquals(expected, result)
        assertEquals(1, engine.callCount)
        assertEquals("127.0.0.1", engine.receivedTarget)
        assertEquals(1_500, engine.receivedTimeoutMs)
    }
}
