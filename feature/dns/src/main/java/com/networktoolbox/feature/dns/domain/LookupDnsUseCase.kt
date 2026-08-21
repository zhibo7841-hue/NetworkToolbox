package com.networktoolbox.feature.dns.domain

import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.dns.DnsResult
import javax.inject.Inject

class LookupDnsUseCase @Inject constructor(
    private val dnsEngine: DnsEngine,
) {
    suspend operator fun invoke(domain: String): DnsResult = dnsEngine.lookup(domain)
}
