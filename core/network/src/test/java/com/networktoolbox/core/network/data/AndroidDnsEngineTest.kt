package com.networktoolbox.core.network.data

import com.networktoolbox.core.network.dns.DnsMethod
import com.networktoolbox.core.network.dns.DnsRecord
import com.networktoolbox.core.network.dns.DnsRecordType
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDnsEngineTest {
    @Test
    fun resolverResultIsReturnedAsARecord() = runBlocking {
        var receivedDomain: String? = null
        val engine = AndroidDnsEngine { domain ->
            receivedDomain = domain
            listOf(DnsRecord(DnsRecordType.A, "93.184.216.34"))
        }

        val result = engine.lookup("example.test")

        assertEquals("example.test", receivedDomain)
        assertTrue(result.success)
        assertEquals("example.test", result.domain)
        assertEquals(listOf(DnsRecord(DnsRecordType.A, "93.184.216.34")), result.records)
        assertEquals(DnsMethod.SYSTEM_RESOLVER, result.method)
        assertNotNull(result.durationMs)
        assertNull(result.errorMessage)
    }

    @Test
    fun resolverResultIsReturnedAsAaaaRecord() = runBlocking {
        val engine = AndroidDnsEngine {
            listOf(DnsRecord(DnsRecordType.AAAA, "2001:db8::1"))
        }

        val result = engine.lookup("ipv6.example.test")

        assertTrue(result.success)
        assertEquals(listOf(DnsRecord(DnsRecordType.AAAA, "2001:db8::1")), result.records)
        assertEquals(DnsRecordType.AAAA, result.records.single().type)
    }

    @Test
    fun resolverCanReturnBothSupportedRecordTypes() = runBlocking {
        val engine = AndroidDnsEngine {
            listOf(
                DnsRecord(DnsRecordType.A, "192.0.2.10"),
                DnsRecord(DnsRecordType.AAAA, "2001:db8::10"),
            )
        }

        val result = engine.lookup("dual-stack.example.test")

        assertTrue(result.success)
        assertEquals(2, result.records.size)
        assertEquals(setOf(DnsRecordType.A, DnsRecordType.AAAA), result.records.map { it.type }.toSet())
    }

    @Test
    fun resolverFailureReturnsFailedSystemResolverResult() = runBlocking {
        val engine = AndroidDnsEngine {
            throw UnknownHostException("not found")
        }

        val result = engine.lookup("missing.example.test")

        assertFalse(result.success)
        assertEquals(emptyList<DnsRecord>(), result.records)
        assertEquals(DnsMethod.SYSTEM_RESOLVER, result.method)
        assertEquals("Domain could not be resolved.", result.errorMessage)
        assertNotNull(result.durationMs)
    }

    @Test
    fun emptyDomainReturnsInvalidResultWithoutCallingResolver() = runBlocking {
        var resolverCalled = false
        val engine = AndroidDnsEngine {
            resolverCalled = true
            emptyList()
        }

        val result = engine.lookup(" ")

        assertFalse(result.success)
        assertEquals("", result.domain)
        assertEquals(DnsMethod.UNAVAILABLE, result.method)
        assertEquals("Invalid domain.", result.errorMessage)
        assertFalse(resolverCalled)
    }

    @Test
    fun malformedDomainReturnsInvalidResultWithoutCallingResolver() = runBlocking {
        var resolverCalled = false
        val engine = AndroidDnsEngine {
            resolverCalled = true
            emptyList()
        }

        val result = engine.lookup("abc..123")

        assertFalse(result.success)
        assertEquals(DnsMethod.UNAVAILABLE, result.method)
        assertEquals("Invalid domain.", result.errorMessage)
        assertFalse(resolverCalled)
    }

    @Test
    fun emptyResolverResultIsReportedAsFailedLookup() = runBlocking {
        val engine = AndroidDnsEngine { emptyList() }

        val result = engine.lookup("no-records.example.test")

        assertFalse(result.success)
        assertEquals(DnsMethod.SYSTEM_RESOLVER, result.method)
        assertEquals("No A or AAAA records found.", result.errorMessage)
        assertNotNull(result.durationMs)
    }
}
