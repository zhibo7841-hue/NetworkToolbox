package com.networktoolbox.core.network.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingStatisticsCalculatorTest {
    private val calculator = PingStatisticsCalculator()

    @Test
    fun oneHundredSuccessfulAttemptsHaveZeroLoss() {
        val result = calculator.calculate(
            request = continuousRequest(count = 100),
            attempts = List(100) { successfulAttempt(latencyMs = 20L) },
            startTime = 1_000L,
            endTime = 2_000L,
        )

        assertEquals(100, result.sentPackets)
        assertEquals(100, result.receivedPackets)
        assertEquals(0, result.lostPackets)
        assertEquals(0.0, result.packetLoss, 0.0)
    }

    @Test
    fun ninetyEightSuccessfulAttemptsHaveTwoPercentLoss() {
        val attempts = List(98) { successfulAttempt(latencyMs = 20L) } +
            List(2) { failedAttempt() }

        val result = calculator.calculate(
            request = continuousRequest(count = 100),
            attempts = attempts,
            startTime = 1_000L,
            endTime = 2_000L,
        )

        assertEquals(100, result.sentPackets)
        assertEquals(98, result.receivedPackets)
        assertEquals(2, result.lostPackets)
        assertEquals(2.0, result.packetLoss, 0.0)
    }

    @Test
    fun allFailedAttemptsDoNotDivideByZero() {
        val result = calculator.calculate(
            request = continuousRequest(count = 100),
            attempts = List(100) { failedAttempt() },
            startTime = 1_000L,
            endTime = 2_000L,
        )

        assertEquals(100, result.sentPackets)
        assertEquals(0, result.receivedPackets)
        assertEquals(100, result.lostPackets)
        assertEquals(100.0, result.packetLoss, 0.0)
        assertNull(result.minLatencyMs)
        assertNull(result.avgLatencyMs)
        assertNull(result.maxLatencyMs)
        assertNull(result.jitterMs)
        assertEquals(PingQualityLevel.UNKNOWN, result.qualityLevel)
    }

    @Test
    fun latencyStatisticsAndJitterUseSuccessfulSamples() {
        val result = calculator.calculate(
            request = continuousRequest(count = 3),
            attempts = listOf(
                successfulAttempt(latencyMs = 10L),
                successfulAttempt(latencyMs = 20L),
                successfulAttempt(latencyMs = 30L),
            ),
            startTime = 1_000L,
            endTime = 2_000L,
        )

        assertEquals(10L, result.minLatencyMs)
        assertEquals(20.0, result.avgLatencyMs!!, 0.0)
        assertEquals(30L, result.maxLatencyMs)
        assertEquals(10.0, result.jitterMs!!, 0.0)
        assertTrue(result.summary.isNotBlank())
    }

    private fun continuousRequest(count: Int): PingRequest = PingRequest(
        target = "example.com",
        protocol = PingProtocol.IPV4,
        mode = PingMode.CONTINUOUS,
        count = count,
        intervalMs = 0,
        timeoutMs = 3_000,
    )

    private fun successfulAttempt(latencyMs: Long): PingAttemptResult = PingAttemptResult(
        target = "example.com",
        address = "192.0.2.10",
        protocol = PingProtocol.IPV4,
        success = true,
        latencyMs = latencyMs,
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = null,
    )

    private fun failedAttempt(): PingAttemptResult = PingAttemptResult(
        target = "example.com",
        address = "192.0.2.10",
        protocol = PingProtocol.IPV4,
        success = false,
        latencyMs = null,
        method = PingMethod.SYSTEM_REACHABILITY,
        errorMessage = "Timeout",
    )
}
