package com.networktoolbox.core.network.dns

data class DnsRecord(
    val type: DnsRecordType,
    val value: String,
)
