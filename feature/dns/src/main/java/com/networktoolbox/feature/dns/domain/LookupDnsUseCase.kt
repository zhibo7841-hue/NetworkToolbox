package com.networktoolbox.feature.dns.domain

import com.networktoolbox.core.common.history.HistoryRecordFactory
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsResult
import javax.inject.Inject

class LookupDnsUseCase @Inject constructor(
    private val dnsEngine: DnsEngine,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        domain: String,
        persistHistory: Boolean = true,
    ): DnsResult = dnsEngine.lookup(domain)
        .also { result ->
            if (!persistHistory) return@also

            val aRecords = result.records
                .filter { it.type == DnsRecordType.A }
                .map { it.value }
            val aaaaRecords = result.records
                .filter { it.type == DnsRecordType.AAAA }
                .map { it.value }
            historyRecorder.record(
                HistoryRecordFactory.dns(
                    timestamp = System.currentTimeMillis(),
                    domain = result.domain,
                    success = result.success,
                    aRecords = aRecords,
                    aaaaRecords = aaaaRecords,
                    durationMs = result.durationMs,
                ),
            )
        }
}
