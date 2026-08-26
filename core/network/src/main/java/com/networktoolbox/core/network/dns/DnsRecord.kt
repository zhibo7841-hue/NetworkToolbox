package com.networktoolbox.core.network.dns

data class DnsRecord(
    val type: DnsRecordType,
    val value: String,
    val name: String = "",
    val ttl: Long? = null,
    val priority: Int? = null,
    val txtSegments: List<String> = emptyList(),
) {
    val ttlSeconds: Long? get() = ttl
}
