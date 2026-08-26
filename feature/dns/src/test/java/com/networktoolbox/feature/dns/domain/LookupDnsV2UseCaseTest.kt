package com.networktoolbox.feature.dns.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.dns.DnsLookupResult
import com.networktoolbox.core.network.dns.DnsLookupStatus
import com.networktoolbox.core.network.dns.DnsQueryMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsServerInfo
import com.networktoolbox.feature.dns.FakeDnsQueryEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupDnsV2UseCaseTest {
    @Test
    fun callsV2EngineAndSavesOneHistoryRecord() = runTest {
        val expected = result(
            records = listOf(
                DnsRecord(
                    type = DnsRecordType.A,
                    name = "example.com",
                    value = "192.0.2.10",
                    ttl = 300,
                ),
                DnsRecord(
                    type = DnsRecordType.AAAA,
                    name = "example.com",
                    value = "2001:db8::10",
                    ttl = 300,
                ),
            ),
        )
        val engine = FakeDnsQueryEngine(expected)
        val savedRecords = mutableListOf<HistoryRecord>()
        val useCase = LookupDnsV2UseCase(
            dnsQueryEngine = engine,
            historyRecorder = HistoryRecorder { savedRecords += it },
        )

        val result = useCase(" example.com ")

        assertEquals(expected, result)
        assertEquals(1, engine.callCount)
        assertEquals(" example.com ", engine.receivedRequest?.queryName)
        assertEquals(
            setOf(DnsRecordType.A, DnsRecordType.AAAA),
            engine.receivedRequest?.recordTypes,
        )
        assertEquals(1, savedRecords.size)
        assertEquals(HistoryType.DNS, savedRecords.single().type)
        assertEquals("DNS 查询 · example.com", savedRecords.single().title)
        assertEquals("解析成功", savedRecords.single().summary)
        assertTrue(savedRecords.single().detailJson.contains("\"recordCounts\":{\"A\":1, \"AAAA\":1}"))
        assertTrue(savedRecords.single().detailJson.contains("\"ttlSeconds\":300"))
    }

    @Test
    fun savesFailureWithHumanReadableSummaryAndStatus() = runTest {
        val expected = result(
            status = DnsLookupStatus.NXDOMAIN,
            errorMessage = "DNS response reported NXDOMAIN.",
        )
        val savedRecords = mutableListOf<HistoryRecord>()
        val useCase = LookupDnsV2UseCase(
            dnsQueryEngine = FakeDnsQueryEngine(expected),
            historyRecorder = HistoryRecorder { savedRecords += it },
        )

        useCase("missing.example")

        assertEquals("域名不存在", savedRecords.single().summary)
        assertTrue(savedRecords.single().detailJson.contains("\"status\":\"NXDOMAIN\""))
    }

    private fun result(
        status: DnsLookupStatus = DnsLookupStatus.SUCCESS,
        records: List<DnsRecord> = emptyList(),
        errorMessage: String? = null,
    ) = DnsLookupResult(
        queryName = "example.com",
        requestedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
        records = records,
        server = DnsServerInfo(
            configuredAddresses = listOf("10.0.0.1"),
            privateDnsActive = true,
            privateDnsServerName = "dns.example",
        ),
        method = DnsQueryMethod.ANDROID_DNS_RESOLVER,
        status = status,
        durationMs = 19,
        startTime = 1,
        endTime = 20,
        errorMessage = errorMessage,
    )
}
