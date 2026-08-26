package com.networktoolbox.feature.dns.domain

import com.networktoolbox.core.common.history.DnsHistoryRecord
import com.networktoolbox.core.common.history.HistoryRecordFactory
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.dns.DnsLookupRequest
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryEngine
import com.networktoolbox.core.network.dns.DnsRecordType
import javax.inject.Inject

class LookupDnsV2UseCase @Inject constructor(
    private val dnsQueryEngine: DnsQueryEngine,
    private val historyRecorder: HistoryRecorder,
) {
    suspend operator fun invoke(
        domain: String,
        recordTypes: Set<DnsRecordType> = DEFAULT_RECORD_TYPES,
        persistHistory: Boolean = true,
    ): DnsLookupResult {
        val result = dnsQueryEngine.lookup(
            DnsLookupRequest(
                queryName = domain,
                recordTypes = recordTypes,
            ),
        )
        if (persistHistory) {
            historyRecorder.record(result.toHistoryRecord())
        }
        return result
    }

    private fun DnsLookupResult.toHistoryRecord() = HistoryRecordFactory.dnsV2(
        timestamp = endTime,
        domain = queryName,
        status = status.name,
        queryTypes = requestedTypes.map(DnsRecordType::name),
        records = records.map { record ->
            DnsHistoryRecord(
                type = record.type.name,
                name = record.name,
                value = record.value,
                ttlSeconds = record.ttlSeconds,
                priority = record.priority,
                txtSegments = record.txtSegments,
            )
        },
        durationMs = durationMs,
        summary = summaryFor(status),
        method = method.name,
        errorMessage = errorMessage,
        configuredDnsServers = server?.configuredAddresses.orEmpty(),
        privateDnsActive = server?.privateDnsActive,
        privateDnsServerName = server?.privateDnsServerName,
    )

    companion object {
        val DEFAULT_RECORD_TYPES: Set<DnsRecordType> =
            linkedSetOf(DnsRecordType.A, DnsRecordType.AAAA)
    }
}

fun summaryFor(status: DnsLookupStatus): String = when (status) {
    DnsLookupStatus.SUCCESS -> "解析成功"
    DnsLookupStatus.PARTIAL -> "部分解析成功"
    DnsLookupStatus.NO_RECORDS -> "查询完成，但没有找到所选记录"
    DnsLookupStatus.NXDOMAIN -> "域名不存在"
    DnsLookupStatus.TIMEOUT -> "DNS 查询超时"
    DnsLookupStatus.NETWORK_ERROR -> "网络异常，无法完成 DNS 查询"
    DnsLookupStatus.INVALID_QUERY -> "域名格式不正确"
    DnsLookupStatus.INVALID_RESPONSE -> "收到的 DNS 响应无法识别"
    DnsLookupStatus.FAILED -> "无法解析该域名"
}
