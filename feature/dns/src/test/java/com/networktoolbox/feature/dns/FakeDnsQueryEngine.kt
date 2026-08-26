package com.networktoolbox.feature.dns

import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsQueryEngine

class FakeDnsQueryEngine(
    private val response: DnsLookupResult,
) : DnsQueryEngine {
    var callCount: Int = 0
        private set
    var receivedRequest: DnsLookupRequest? = null
        private set

    override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult {
        callCount += 1
        receivedRequest = request
        return response
    }
}
