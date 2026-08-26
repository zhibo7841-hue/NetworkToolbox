package com.networktoolbox.core.network.dns

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDnsQueryEngineTest {
    @Test
    fun aggregatesAAndAaaaResultsAndKeepsServerContext() = runBlocking {
        val transport = FakeDnsRawQueryTransport(
            responses = mapOf(
                DnsRecordType.A to DnsResponseFixtures.aResponse(),
                DnsRecordType.AAAA to DnsResponseFixtures.aaaaResponse(),
            ),
        )
        val engine = DefaultDnsQueryEngine(
            transport = transport,
            serverInfoProvider = DnsServerInfoProvider {
                DnsServerInfo(configuredAddresses = listOf("192.0.2.53"))
            },
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            ),
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(DnsQueryMethod.ANDROID_DNS_RESOLVER, result.method)
        assertEquals(listOf("192.0.2.53"), result.server?.configuredAddresses)
        assertEquals(setOf(DnsRecordType.A, DnsRecordType.AAAA), result.records.map { it.type }.toSet())
        assertEquals(2, transport.calls.size)
        assertTrue(result.durationMs != null)
    }

    @Test
    fun mapsTransportTimeoutWithoutThrowing() = runBlocking {
        val engine = DefaultDnsQueryEngine(
            transport = FakeDnsRawQueryTransport(
                failures = mapOf(
                    DnsRecordType.A to DnsTransportException(
                        DnsLookupStatus.TIMEOUT,
                        "DNS query timed out.",
                    ),
                ),
            ),
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A),
            ),
        )

        assertEquals(DnsLookupStatus.TIMEOUT, result.status)
        assertFalse(result.errorMessage.isNullOrBlank())
    }

    @Test
    fun returnsPartialWhenOneRequestedTypeSucceedsAndAnotherFails() = runBlocking {
        val engine = DefaultDnsQueryEngine(
            transport = FakeDnsRawQueryTransport(
                responses = mapOf(DnsRecordType.A to DnsResponseFixtures.aResponse()),
                failures = mapOf(
                    DnsRecordType.AAAA to DnsTransportException(
                        DnsLookupStatus.TIMEOUT,
                        "DNS query timed out.",
                    ),
                ),
            ),
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            ),
        )

        assertEquals(DnsLookupStatus.PARTIAL, result.status)
        assertEquals(1, result.records.size)
        assertEquals(DnsRecordType.A, result.records.single().type)
    }

    @Test
    fun treatsNoRecordsForAnotherTypeAsSuccessfulOverall() = runBlocking {
        val engine = DefaultDnsQueryEngine(
            transport = FakeDnsRawQueryTransport(
                responses = mapOf(
                    DnsRecordType.A to DnsResponseFixtures.aResponse(),
                    DnsRecordType.AAAA to DnsResponseFixtures.response(
                        recordType = DnsRecordType.AAAA,
                        records = emptyList(),
                    ),
                ),
            ),
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            ),
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(1, result.records.size)
        assertEquals(DnsRecordType.A, result.records.single().type)
    }

    @Test
    fun reportsNoRecordsWhenAllTypesCompleteWithoutRecords() = runBlocking {
        val engine = DefaultDnsQueryEngine(
            transport = FakeDnsRawQueryTransport(
                responses = mapOf(
                    DnsRecordType.A to DnsResponseFixtures.response(
                        recordType = DnsRecordType.A,
                        records = emptyList(),
                    ),
                    DnsRecordType.AAAA to DnsResponseFixtures.response(
                        recordType = DnsRecordType.AAAA,
                        records = emptyList(),
                    ),
                ),
            ),
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            ),
        )

        assertEquals(DnsLookupStatus.NO_RECORDS, result.status)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun deduplicatesRecordsAcrossResponsesAndKeepsSmallestTtl() = runBlocking {
        val duplicateCname = DnsResponseFixtures.encodedNameWithExamplePointer("alias")
        val transport = FakeDnsRawQueryTransport(
            responses = mapOf(
                DnsRecordType.A to DnsResponseFixtures.response(
                    recordType = DnsRecordType.A,
                    records = listOf(
                        DnsResponseFixtures.resourceRecord(
                            type = DnsRecordType.A,
                            ttl = 300,
                            data = byteArrayOf(93, 184.toByte(), 216.toByte(), 34),
                        ),
                        DnsResponseFixtures.resourceRecord(
                            type = DnsRecordType.CNAME,
                            ttl = 1_065,
                            data = duplicateCname,
                        ),
                    ),
                ),
                DnsRecordType.AAAA to DnsResponseFixtures.response(
                    recordType = DnsRecordType.AAAA,
                    records = listOf(
                        DnsResponseFixtures.resourceRecord(
                            type = DnsRecordType.AAAA,
                            ttl = 600,
                            data = byteArrayOf(
                                0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
                                0, 0, 0, 0, 0, 0, 0, 1,
                            ),
                        ),
                        DnsResponseFixtures.resourceRecord(
                            type = DnsRecordType.CNAME,
                            ttl = 1_064,
                            data = duplicateCname,
                        ),
                    ),
                ),
            ),
        )

        val result = DefaultDnsQueryEngine(transport).lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA),
            ),
        )

        val cnameRecords = result.records.filter { it.type == DnsRecordType.CNAME }
        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(3, result.records.size)
        assertEquals(1, cnameRecords.size)
        assertEquals(1_064L, cnameRecords.single().ttl)
    }

    @Test
    fun mapsNxdomainResponse() = runBlocking {
        val engine = DefaultDnsQueryEngine(
            transport = FakeDnsRawQueryTransport(
                responses = mapOf(DnsRecordType.A to DnsResponseFixtures.nxdomainResponse()),
            ),
        )

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = setOf(DnsRecordType.A),
            ),
        )

        assertEquals(DnsLookupStatus.NXDOMAIN, result.status)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun invalidQueryDoesNotCallTransport() = runBlocking {
        val transport = FakeDnsRawQueryTransport()
        val engine = DefaultDnsQueryEngine(transport)

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = "abc..123",
                recordTypes = setOf(DnsRecordType.A),
            ),
        )

        assertEquals(DnsLookupStatus.INVALID_QUERY, result.status)
        assertEquals(0, transport.calls.size)
        assertNull(result.server)
    }

    @Test
    fun supportsEachCoreRecordTypeAsAnIndependentQuery() = runBlocking {
        val responses = mapOf(
            DnsRecordType.A to DnsResponseFixtures.aResponse(),
            DnsRecordType.AAAA to DnsResponseFixtures.aaaaResponse(),
            DnsRecordType.CNAME to DnsResponseFixtures.cnameResponse(),
            DnsRecordType.MX to DnsResponseFixtures.mxResponse(),
            DnsRecordType.TXT to DnsResponseFixtures.txtResponse(),
        )
        val transport = FakeDnsRawQueryTransport(responses = responses)
        val engine = DefaultDnsQueryEngine(transport)

        val result = engine.lookup(
            DnsLookupRequest(
                queryName = DnsResponseFixtures.EXAMPLE_COM,
                recordTypes = responses.keys,
            ),
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(5, result.records.size)
        assertEquals(responses.keys, transport.calls.toSet())
    }

    private class FakeDnsRawQueryTransport(
        private val responses: Map<DnsRecordType, ByteArray> = emptyMap(),
        private val failures: Map<DnsRecordType, DnsTransportException> = emptyMap(),
    ) : DnsRawQueryTransport {
        val calls = mutableListOf<DnsRecordType>()

        override suspend fun query(
            queryName: String,
            recordType: DnsRecordType,
            timeoutMs: Int,
        ): ByteArray {
            calls += recordType
            failures[recordType]?.let { throw it }
            return responses.getValue(recordType)
        }
    }
}
