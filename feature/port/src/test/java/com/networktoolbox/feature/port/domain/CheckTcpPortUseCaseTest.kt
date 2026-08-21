package com.networktoolbox.feature.port.domain

import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.port.FakeTcpPortChecker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckTcpPortUseCaseTest {
    @Test
    fun delegatesValidHostAndPortToChecker() = runTest {
        val expected = TcpProbeResult(
            host = "192.0.2.10",
            port = 443,
            success = true,
            latencyMs = 8,
            errorMessage = null,
        )
        val checker = FakeTcpPortChecker(expected)
        val useCase = CheckTcpPortUseCase(checker)

        val result = useCase("192.0.2.10", "443", timeoutMs = 1_500)

        assertEquals(expected, result)
        assertEquals(1, checker.callCount)
        assertEquals("192.0.2.10", checker.receivedHost)
        assertEquals(443, checker.receivedPort)
        assertEquals(1_500, checker.receivedTimeoutMs)
    }

    @Test
    fun invalidPortInputReturnsErrorWithoutCallingChecker() = runTest {
        val checker = FakeTcpPortChecker(
            TcpProbeResult("192.0.2.10", 443, true, 8, null),
        )
        val useCase = CheckTcpPortUseCase(checker)

        val result = useCase("192.0.2.10", "")

        assertEquals(false, result.success)
        assertEquals(0, result.port)
        assertEquals("Invalid port.", result.errorMessage)
        assertEquals(0, checker.callCount)
    }
}
