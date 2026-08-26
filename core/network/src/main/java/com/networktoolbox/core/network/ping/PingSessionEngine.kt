package com.networktoolbox.core.network.ping

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

fun interface PingProbe {
    suspend fun probe(
        target: String,
        protocol: PingProtocol,
        timeoutMs: Int,
    ): PingAttemptResult
}

fun interface PingClock {
    fun now(): Long
}

interface PingSessionEngine {
    suspend fun run(
        request: PingRequest,
        onProgress: (PingSessionProgress) -> Unit = {},
    ): PingSessionResult
}

class DefaultPingSessionEngine(
    private val probe: PingProbe,
    private val statisticsCalculator: PingStatisticsCalculator = PingStatisticsCalculator(),
    private val clock: PingClock = PingClock { System.currentTimeMillis() },
    private val waitBetweenAttempts: suspend (Long) -> Unit = { delay(it) },
) : PingSessionEngine {
    override suspend fun run(
        request: PingRequest,
        onProgress: (PingSessionProgress) -> Unit,
    ): PingSessionResult {
        validate(request)

        val startTime = clock.now()
        val attempts = mutableListOf<PingAttemptResult>()
        while (shouldRunAnotherAttempt(request, attempts.size)) {
            currentCoroutineContext().ensureActive()
            attempts += probe.probe(
                target = request.target,
                protocol = request.protocol,
                timeoutMs = request.timeoutMs,
            )
            onProgress(statisticsCalculator.calculateProgress(request, attempts))

            if (!shouldRunAnotherAttempt(request, attempts.size)) break
            if (request.intervalMs > 0) {
                waitBetweenAttempts(request.intervalMs.toLong())
            }
        }

        return statisticsCalculator.calculate(
            request = request,
            attempts = attempts,
            startTime = startTime,
            endTime = clock.now(),
        )
    }

    private fun shouldRunAnotherAttempt(
        request: PingRequest,
        completedAttempts: Int,
    ): Boolean = when (request.mode) {
        PingMode.SINGLE -> completedAttempts == 0
        PingMode.CONTINUOUS -> request.count == null || completedAttempts < request.count
    }

    private fun validate(request: PingRequest) {
        require(request.timeoutMs > 0) { "Timeout must be greater than zero." }
        require(request.intervalMs >= 0) { "Interval must not be negative." }
        if (request.mode == PingMode.CONTINUOUS) {
            require(request.count == null || request.count > 0) {
                "Continuous count must be positive or null."
            }
        }
    }
}
