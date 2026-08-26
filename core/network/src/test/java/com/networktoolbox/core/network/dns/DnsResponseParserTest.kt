package com.networktoolbox.core.network.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResponseParserTest {
    private val parser = DnsResponseParser()

    @Test
    fun parsesARecordAnd32BitTtl() {
        val result = parser.parse(
            response = DnsResponseFixtures.aResponse(ttl = 0xf1234567L),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(1, result.records.size)
        assertEquals(DnsRecordType.A, result.records.single().type)
        assertEquals("example.com", result.records.single().name)
        assertEquals("93.184.216.34", result.records.single().value)
        assertEquals(4_045_620_583L, result.records.single().ttlSeconds)
    }

    @Test
    fun parsesAaaaRecord() {
        val result = parser.parse(
            response = DnsResponseFixtures.aaaaResponse(),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.AAAA,
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals("2001:db8::1", result.records.single().value)
        assertEquals(600L, result.records.single().ttlSeconds)
    }

    @Test
    fun parsesCnameWithCompressedOwnerAndTargetName() {
        val result = parser.parse(
            response = DnsResponseFixtures.cnameResponse(),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.CNAME,
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals(DnsRecordType.CNAME, result.records.single().type)
        assertEquals("alias.example.com", result.records.single().value)
        assertEquals(120L, result.records.single().ttlSeconds)
    }

    @Test
    fun parsesMxPriorityAndCompressedExchangeName() {
        val result = parser.parse(
            response = DnsResponseFixtures.mxResponse(),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.MX,
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals("mail.example.com", result.records.single().value)
        assertEquals(10, result.records.single().priority)
        assertEquals(900L, result.records.single().ttlSeconds)
    }

    @Test
    fun preservesAllTxtCharacterStrings() {
        val result = parser.parse(
            response = DnsResponseFixtures.txtResponse(),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.TXT,
        )

        assertEquals(DnsLookupStatus.SUCCESS, result.status)
        assertEquals("helloworld", result.records.single().value)
        assertEquals(listOf("hello", "world"), result.records.single().txtSegments)
    }

    @Test
    fun mapsRcodeThreeToNxdomain() {
        val result = parser.parse(
            response = DnsResponseFixtures.nxdomainResponse(),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.NXDOMAIN, result.status)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun mapsSuccessfulResponseWithoutRequestedRecordsToNoRecords() {
        val result = parser.parse(
            response = DnsResponseFixtures.response(
                recordType = DnsRecordType.A,
                records = emptyList(),
            ),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.NO_RECORDS, result.status)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun truncatedPacketReturnsInvalidResponseWithoutThrowing() {
        val result = parser.parse(
            response = byteArrayOf(0x12, 0x34, 0x81.toByte()),
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.INVALID_RESPONSE, result.status)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun outOfBoundsNamePointerReturnsInvalidResponseWithoutThrowing() {
        val response = DnsResponseFixtures.response(
            recordType = DnsRecordType.A,
            records = listOf(
                byteArrayOf(
                    0xc0.toByte(), 0xff.toByte(),
                    0, 1, 0, 1,
                    0, 0, 0, 10,
                    0, 4,
                    192.toByte(), 0, 2, 10,
                ),
            ),
        )

        val result = parser.parse(
            response = response,
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.INVALID_RESPONSE, result.status)
        assertFalse(result.errorMessage.isNullOrBlank())
    }

    @Test
    fun cyclicQuestionPointerReturnsInvalidResponseWithoutThrowing() {
        val response = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(),
            0, 1, 0, 0, 0, 0, 0, 0,
            0xc0.toByte(), 0x0c,
            0, 1, 0, 1,
        )

        val result = parser.parse(
            response = response,
            queryName = DnsResponseFixtures.EXAMPLE_COM,
            recordType = DnsRecordType.A,
        )

        assertEquals(DnsLookupStatus.INVALID_RESPONSE, result.status)
    }
}
