package com.networktoolbox.feature.lanscan.domain

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpDiagnosticsTest {
    @Test
    fun `http status keeps successful response and classifies failures`() {
        assertNull(UpnpHttpResponsePolicy.failureCategory(200))
        assertEquals(UpnpFailureCategory.REDIRECT_REJECTED, UpnpHttpResponsePolicy.failureCategory(302))
        assertEquals(UpnpFailureCategory.HTTP_ERROR, UpnpHttpResponsePolicy.failureCategory(404))
    }

    @Test
    fun `content type is metadata and does not reject supported body variants`() {
        listOf(null, "text/xml; charset=utf-8", "application/xml", "application/octet-stream").forEach {
            assertTrue(UpnpHttpResponsePolicy.acceptsContentType(it))
        }
    }

    @Test
    fun `transport failures distinguish refused timeout and unreachable`() {
        assertEquals(
            UpnpFailureCategory.CONNECTION_REFUSED,
            UpnpTransportFailureClassifier.connect(ConnectException("Connection refused")),
        )
        assertEquals(
            UpnpFailureCategory.CONNECT_TIMEOUT,
            UpnpTransportFailureClassifier.connect(SocketTimeoutException("connect timed out")),
        )
        assertEquals(
            UpnpFailureCategory.NETWORK_UNREACHABLE,
            UpnpTransportFailureClassifier.connect(NoRouteToHostException("No route to host")),
        )
        assertEquals(
            UpnpFailureCategory.READ_TIMEOUT,
            UpnpTransportFailureClassifier.read(SocketTimeoutException("read timed out")),
        )
    }

    @Test
    fun `location validation reports distinct unsafe causes`() {
        assertEquals(
            UpnpFailureCategory.UNSUPPORTED_SCHEME,
            UpnpLocationValidator.validateDetailed("ftp://10.0.1.1/device.xml", "10.0.1.1").failureCategory,
        )
        assertEquals(
            UpnpFailureCategory.LOCATION_HOST_MISMATCH,
            UpnpLocationValidator.validateDetailed("http://10.0.1.2/device.xml", "10.0.1.1").failureCategory,
        )
        assertEquals(
            UpnpFailureCategory.INVALID_LOCATION,
            UpnpLocationValidator.validateDetailed("not a url", "10.0.1.1").failureCategory,
        )
    }

    @Test
    fun `parser preserves xml declared encoding from response bytes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-16"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device><friendlyName>UTF16 Router</friendlyName></device>
            </root>
        """.trimIndent().toByteArray(StandardCharsets.UTF_16)

        val result = UpnpDescriptionParser.parseWithDiagnostics(xml)

        assertEquals("UTF16 Router", result.description?.friendlyName)
        assertNull(result.failureCategory)
    }

    @Test
    fun `parser reports no root identity and embedded device without promoting it`() {
        val result = UpnpDescriptionParser.parseWithDiagnostics(
            """
            <root>
              <device>
                <deviceList><device><friendlyName>Embedded only</friendlyName></device></deviceList>
              </device>
            </root>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )

        assertEquals(UpnpFailureCategory.NO_IDENTITY_FIELDS, result.failureCategory)
        assertTrue(result.rootDevicePresent)
        assertTrue(result.embeddedDevicePresent)
        assertFalse(result.description?.hasIdentity == true)
    }

    @Test
    fun `parser classifies empty oversized and security rejected responses`() {
        assertEquals(
            UpnpFailureCategory.EMPTY_RESPONSE,
            UpnpDescriptionParser.parseWithDiagnostics(ByteArray(0)).failureCategory,
        )
        assertEquals(
            UpnpFailureCategory.RESPONSE_TOO_LARGE,
            UpnpDescriptionParser.parseWithDiagnostics(
                ByteArray(UpnpDescriptionParser.MAX_XML_BYTES + 1),
            ).failureCategory,
        )
        assertEquals(
            UpnpFailureCategory.XML_SECURITY_REJECTED,
            UpnpDescriptionParser.parseWithDiagnostics(
                "<!DOCTYPE root [<!ENTITY x \"blocked\">]><root><device><friendlyName>&x;</friendlyName></device></root>"
                    .toByteArray(StandardCharsets.UTF_8),
            ).failureCategory,
        )
    }
}
