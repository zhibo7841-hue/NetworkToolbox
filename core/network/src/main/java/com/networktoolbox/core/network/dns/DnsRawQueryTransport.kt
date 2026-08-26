package com.networktoolbox.core.network.dns

interface DnsRawQueryTransport {
    suspend fun query(
        queryName: String,
        recordType: DnsRecordType,
        timeoutMs: Int,
    ): ByteArray
}

class DnsTransportException(
    val status: DnsLookupStatus,
    override val message: String,
) : Exception(message)
