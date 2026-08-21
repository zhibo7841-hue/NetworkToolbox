package com.networktoolbox.core.network.dns

data class DnsResult(
    val domain: String,
    val success: Boolean,
    val records: List<DnsRecord>,
    val durationMs: Long?,
    val method: DnsMethod,
    val errorMessage: String?,
)
