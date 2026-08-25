package com.networktoolbox.feature.ping.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
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
            target = "8.8.8.8",
            success = true,
            latencyMs = 1L,
            method = PingMethod.SYSTEM_REACHABILITY,
            errorMessage = null,
        )
        val engine = FakePingEngine(expected)
        val savedRecords = mutableListOf<HistoryRecord>()
        val useCase = ExecutePingUseCase(
            pingEngine = engine,
            historyRecorder = HistoryRecorder { savedRecords += it },
        )

        val result = useCase("8.8.8.8", timeoutMs = 1_500)

        assertEquals(expected, result)
        assertEquals(1, engine.callCount)
        assertEquals("8.8.8.8", engine.receivedTarget)
        assertEquals(1_500, engine.receivedTimeoutMs)
        assertEquals(HistoryType.PING, savedRecords.single().type)
        assertEquals("Ping · 8.8.8.8", savedRecords.single().title)
    }
}
