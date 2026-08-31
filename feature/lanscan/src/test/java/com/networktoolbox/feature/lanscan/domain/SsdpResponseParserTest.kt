package com.networktoolbox.feature.lanscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpResponseParserTest {
    @Test
    fun `builds standards compatible search request`() {
        val message = SsdpMessageBuilder.mSearch(mxSeconds = 1)

        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(message.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(message.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(message.contains("MX: 1\r\n"))
        assertTrue(message.contains("ST: ssdp:all\r\n"))
        assertTrue(message.endsWith("\r\n\r\n"))
        assertFalse(message.contains("\\r"))
    }

    @Test
    fun `parses case insensitive unordered headers and optional fields`() {
        val response = requireNotNull(
            SsdpResponseParser.parse(
                sourceIp = "10.0.1.1",
                rawResponse = """
                    HTTP/1.1 200 OK
                    sT: uuid:root
                    LOCATION: http://10.0.1.1:80/root.xml
                    server: ImmortalWrt/1.0
                    eXt:
                """.trimIndent().replace("\n", "\r\n") + "\r\n",
            ),
        )

        assertEquals("http://10.0.1.1:80/root.xml", response.location)
        assertEquals("uuid:root", response.searchTarget)
        assertEquals("ImmortalWrt/1.0", response.server)
        assertNull(response.cacheControl)
    }

    @Test
    fun `malformed status and oversized packet are ignored safely`() {
        assertNull(
            SsdpResponseParser.parse(
                "10.0.1.1",
                "not HTTP\r\nLOCATION: http://10.0.1.1/root.xml\r\n\r\n",
            ),
        )
        assertNull(
            SsdpResponseParser.parse(
                "10.0.1.1",
                "HTTP/1.1 200 OK\r\nX: ${"x".repeat(SsdpResponseParser.MAX_PACKET_BYTES)}",
            ),
        )

        val parsed = SsdpResponseParser.parse(
            "10.0.1.1",
            "HTTP/1.1 200 OK\r\nbroken header\r\nLOCATION: http://10.0.1.1/root.xml\r\n\r\n",
        )
        assertEquals("http://10.0.1.1/root.xml", parsed?.location)
    }

    @Test
    fun `validates location scheme host and length`() {
        assertEquals(
            "http://10.0.1.1/root.xml",
            UpnpLocationValidator.validate("http://10.0.1.1/root.xml", "10.0.1.1"),
        )
        assertEquals(
            "https://10.0.1.1:443/root.xml",
            UpnpLocationValidator.validate("https://10.0.1.1:443/root.xml", "10.0.1.1"),
        )
        assertNull(UpnpLocationValidator.validate("file:///etc/passwd", "10.0.1.1"))
        assertNull(UpnpLocationValidator.validate("ftp://10.0.1.1/root.xml", "10.0.1.1"))
        assertNull(UpnpLocationValidator.validate("http:///root.xml", "10.0.1.1"))
        assertNull(
            UpnpLocationValidator.validate("http://10.0.1.2/root.xml", "10.0.1.1"),
        )
        assertNull(
            UpnpLocationValidator.validate(
                "http://10.0.1.1/${"x".repeat(UpnpLocationValidator.MAX_LOCATION_LENGTH)}",
                "10.0.1.1",
            ),
        )
    }
}
