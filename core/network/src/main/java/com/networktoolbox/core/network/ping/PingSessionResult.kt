package com.networktoolbox.core.network.ping

data class PingSessionResult(
    val target: String,
    val address: String?,
    val protocol: PingProtocol,
    val mode: PingMode,
    val startTime: Long,
    val endTime: Long,
    val sentPackets: Int,
    val receivedPackets: Int,
    val lostPackets: Int,
    val packetLoss: Double,
    val minLatencyMs: Long?,
    val avgLatencyMs: Double?,
    val maxLatencyMs: Long?,
    val jitterMs: Double?,
    val qualityLevel: PingQualityLevel,
    val summary: String,
    val method: PingMethod,
    val errorMessage: String?,
)
