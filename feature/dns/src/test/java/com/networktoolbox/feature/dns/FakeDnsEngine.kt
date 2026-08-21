package com.networktoolbox.feature.dns

import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.dns.DnsResult

class FakeDnsEngine(
    private val response: DnsResult,
) : DnsEngine {
    var callCount: Int = 0
        private set
    var receivedDomain: String? = null
        private set

    override suspend fun lookup(domain: String): DnsResult {
        callCount += 1
        receivedDomain = domain
        return response
    }
}
