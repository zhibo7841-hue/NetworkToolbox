package com.networktoolbox.feature.dns.domain

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
            domain = "example.test",
            success = true,
            records = listOf(DnsRecord(DnsRecordType.A, "192.0.2.10")),
            durationMs = 12,
            method = DnsMethod.SYSTEM_RESOLVER,
            errorMessage = null,
        )
        val engine = FakeDnsEngine(expected)
        val useCase = LookupDnsUseCase(engine)

        val result = useCase("example.test")

        assertEquals(expected, result)
        assertEquals(1, engine.callCount)
        assertEquals("example.test", engine.receivedDomain)
    }
}
