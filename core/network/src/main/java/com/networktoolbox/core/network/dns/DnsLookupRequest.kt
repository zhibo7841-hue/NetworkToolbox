package com.networktoolbox.core.network.dns

data class DnsLookupRequest(
    val queryName: String,
    val recordTypes: Set<DnsRecordType> = setOf(DnsRecordType.A, DnsRecordType.AAAA),
    val server: DnsServerSelection = DnsServerSelection.SYSTEM_DEFAULT,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000
    }
}
