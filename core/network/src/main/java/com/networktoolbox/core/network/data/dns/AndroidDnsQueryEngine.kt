package com.networktoolbox.core.network.data.dns

import android.content.Context
import com.networktoolbox.core.network.dns.DefaultDnsQueryEngine
import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsQueryEngine

class AndroidDnsQueryEngine(
    context: Context,
) : DnsQueryEngine {
    private val delegate = DefaultDnsQueryEngine(
        transport = AndroidDnsResolverTransport(context),
        serverInfoProvider = AndroidDnsServerInfoProvider(context),
    )

    override suspend fun lookup(request: DnsLookupRequest): DnsLookupResult =
        delegate.lookup(request)
}
