package com.networktoolbox.core.network.ping

data class PingSessionProgress(
    val target: String,
    val sentPackets: Int,
    val receivedPackets: Int,
    val lostPackets: Int,
    val packetLoss: Double,
    val latestLatencyMs: Long?,
    val minLatencyMs: Long?,
    val avgLatencyMs: Double?,
    val maxLatencyMs: Long?,
)
