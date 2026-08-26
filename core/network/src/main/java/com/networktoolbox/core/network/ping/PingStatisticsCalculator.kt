package com.networktoolbox.core.network.ping

import kotlin.math.abs

class PingStatisticsCalculator {
    fun calculateProgress(
        request: PingRequest,
        attempts: List<PingAttemptResult>,
    ): PingSessionProgress {
        val successfulLatencies = attempts
            .filter { it.success }
            .mapNotNull { it.latencyMs }
        val sentPackets = attempts.size
        val receivedPackets = attempts.count { it.success }
        val lostPackets = (sentPackets - receivedPackets).coerceAtLeast(0)

        return PingSessionProgress(
            target = request.target,
            sentPackets = sentPackets,
            receivedPackets = receivedPackets,
            lostPackets = lostPackets,
            packetLoss = packetLossPercentage(sentPackets, lostPackets),
            latestLatencyMs = attempts.lastOrNull()?.latencyMs,
            minLatencyMs = successfulLatencies.minOrNull(),
            avgLatencyMs = successfulLatencies.takeIf { it.isNotEmpty() }?.average(),
            maxLatencyMs = successfulLatencies.maxOrNull(),
        )
    }

    fun calculate(
        request: PingRequest,
        attempts: List<PingAttemptResult>,
        startTime: Long,
        endTime: Long,
    ): PingSessionResult {
        val sentPackets = attempts.size
        val receivedPackets = attempts.count { it.success }
        val lostPackets = (sentPackets - receivedPackets).coerceAtLeast(0)
        val packetLoss = packetLossPercentage(sentPackets, lostPackets)
        val latencies = attempts
            .asSequence()
            .filter { it.success }
            .mapNotNull { it.latencyMs }
            .toList()
        val minLatencyMs = latencies.minOrNull()
        val maxLatencyMs = latencies.maxOrNull()
        val avgLatencyMs = latencies.takeIf { it.isNotEmpty() }?.average()
        val jitterMs = latencies
            .takeIf { it.size >= 2 }
            ?.zipWithNext()
            ?.map { (previous, current) -> abs(current.toDouble() - previous.toDouble()) }
            ?.average()
        val quality = evaluateQuality(packetLoss, avgLatencyMs)
        val protocol = attempts
            .firstOrNull { it.protocol != PingProtocol.AUTO }
            ?.protocol
            ?: request.protocol
        val address = attempts
            .asSequence()
            .mapNotNull { it.address?.takeIf(String::isNotBlank) }
            .firstOrNull()
        val method = attempts.firstOrNull()?.method ?: PingMethod.UNAVAILABLE
        val errorMessage = attempts
            .asSequence()
            .mapNotNull { it.errorMessage?.takeIf(String::isNotBlank) }
            .firstOrNull()

        return PingSessionResult(
            target = request.target,
            address = address,
            protocol = protocol,
            mode = request.mode,
            startTime = startTime,
            endTime = endTime,
            sentPackets = sentPackets,
            receivedPackets = receivedPackets,
            lostPackets = lostPackets,
            packetLoss = packetLoss,
            minLatencyMs = minLatencyMs,
            avgLatencyMs = avgLatencyMs,
            maxLatencyMs = maxLatencyMs,
            jitterMs = jitterMs,
            qualityLevel = quality.level,
            summary = quality.summary,
            method = method,
            errorMessage = errorMessage,
        )
    }

    private fun evaluateQuality(
        packetLoss: Double,
        avgLatencyMs: Double?,
    ): QualityEvaluation {
        if (avgLatencyMs == null) {
            return QualityEvaluation(
                level = PingQualityLevel.UNKNOWN,
                summary = "No successful responses.",
            )
        }

        // These are initial, evidence-oriented heuristics. They are not fault diagnoses.
        val level = when {
            packetLoss == 0.0 && avgLatencyMs <= 50.0 -> PingQualityLevel.EXCELLENT
            packetLoss <= 1.0 && avgLatencyMs <= 100.0 -> PingQualityLevel.GOOD
            packetLoss <= 5.0 && avgLatencyMs <= 200.0 -> PingQualityLevel.FAIR
            else -> PingQualityLevel.POOR
        }
        return QualityEvaluation(
            level = level,
            summary = "${level.name.lowercase().replaceFirstChar(Char::uppercase)} observed network quality.",
        )
    }

    private fun packetLossPercentage(sentPackets: Int, lostPackets: Int): Double =
        if (sentPackets == 0) 0.0 else lostPackets * 100.0 / sentPackets

    private data class QualityEvaluation(
        val level: PingQualityLevel,
        val summary: String,
    )
}
