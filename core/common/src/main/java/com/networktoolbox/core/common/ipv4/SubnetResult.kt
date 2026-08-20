package com.networktoolbox.core.common.ipv4

data class SubnetResult(
    val ipAddress: String,
    val prefixLength: Int,
    val subnetMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val usableRangeStart: String,
    val usableRangeEnd: String,
    val hostCount: Long,
)
