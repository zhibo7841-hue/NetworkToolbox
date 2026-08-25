package com.networktoolbox.feature.dns.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import com.networktoolbox.core.network.dns.DnsResult
import com.networktoolbox.feature.dns.FakeDnsEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LookupDnsUseCaseTest {
    @Test
    fun delegatesDomainAndReturnsEngineResult() = runTest {
        val expected = DnsResult(
            domain = "example.com",
            success = true,
            records = listOf(DnsRecord(DnsRecordType.A, "192.0.2.10")),
            durationMs = 12,
            method = DnsMethod.SYSTEM_RESOLVER,
            errorMessage = null,
        )
        val engine = FakeDnsEngine(expected)
        val savedRecords = mutableListOf<HistoryRecord>()
        val useCase = LookupDnsUseCase(
            dnsEngine = engine,
            historyRecorder = HistoryRecorder { savedRecords += it },
        )

        val result = useCase("example.com")

        assertEquals(expected, result)
        assertEquals(1, engine.callCount)
        assertEquals("example.com", engine.receivedDomain)
        assertEquals(HistoryType.DNS, savedRecords.single().type)
        assertEquals("DNS · example.com", savedRecords.single().title)
    }
}
