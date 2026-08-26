package com.networktoolbox.core.network.dns

interface DnsQueryEngine {
    suspend fun lookup(request: DnsLookupRequest): DnsLookupResult
}
