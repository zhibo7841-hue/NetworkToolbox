package com.networktoolbox.core.network.dns

data class DnsLookupResult(
    val queryName: String,
    val requestedTypes: Set<DnsRecordType>,
    val records: List<DnsRecord>,
    val server: DnsServerInfo?,
    val method: DnsQueryMethod,
    val status: DnsLookupStatus,
    val durationMs: Long?,
    val startTime: Long,
    val endTime: Long,
    val errorMessage: String?,
) {
    val error: String? get() = errorMessage
}
