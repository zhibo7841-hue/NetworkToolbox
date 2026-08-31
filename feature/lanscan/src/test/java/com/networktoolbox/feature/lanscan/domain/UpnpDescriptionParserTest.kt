package com.networktoolbox.feature.lanscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpDescriptionParserTest {
    @Test
    fun `parses root identity and service fields`() {
        val result = requireNotNull(UpnpDescriptionParser.parse(validDescription()))

        assertEquals("Living Room Router", result.friendlyName)
        assertEquals("Example Networks", result.manufacturer)
        assertEquals("Router", result.modelDescription)
        assertEquals("XR-1", result.modelName)
        assertEquals("42", result.modelNumber)
        assertEquals("urn:schemas-upnp-org:device:InternetGatewayDevice:1", result.deviceType)
        assertEquals("uuid:root-device", result.udn)
        assertEquals("https://example.invalid/vendor", result.manufacturerUrl)
        assertEquals("http://10.0.1.1/", result.presentationUrl)
        assertEquals(
            listOf(
                UpnpServiceDescription(
                    serviceType = "urn:schemas-upnp-org:service:WANIPConnection:1",
                    serviceId = "urn:upnp-org:serviceId:WANIPConn1",
                ),
            ),
            result.services,
        )
    }

    @Test
    fun `ignores embedded device identity and keeps root device`() {
        val result = requireNotNull(
            UpnpDescriptionParser.parse(
                """
                <root>
                  <device>
                    <friendlyName>Root</friendlyName>
                    <deviceList>
                      <device><friendlyName>Embedded</friendlyName></device>
                    </deviceList>
                  </device>
                </root>
                """.trimIndent(),
            ),
        )

        assertEquals("Root", result.friendlyName)
    }

    @Test
    fun `allows missing optional identity fields`() {
        val result = requireNotNull(
            UpnpDescriptionParser.parse(
                "<root><device><modelName>Only Model</modelName></device></root>",
            ),
        )

        assertEquals("Only Model", result.modelName)
        assertNull(result.friendlyName)
        assertTrue(result.services.isEmpty())
    }

    @Test
    fun `rejects malformed oversized doctype and deep xml`() {
        assertNull(UpnpDescriptionParser.parse("<root><device>"))
        assertNull(
            UpnpDescriptionParser.parse(
                "<!DOCTYPE root [<!ENTITY x \"secret\">]><root><device><friendlyName>&x;</friendlyName></device></root>",
            ),
        )
        assertNull(
            UpnpDescriptionParser.parse(
                "<root><device><friendlyName>${"x".repeat(3_000)}</friendlyName></device></root>",
            ),
        )

        val deep = buildString {
            append("<root>")
            repeat(UpnpDescriptionParser.MAX_XML_DEPTH + 1) { append("<x>") }
            repeat(UpnpDescriptionParser.MAX_XML_DEPTH + 1) { append("</x>") }
            append("</root>")
        }
        assertNull(UpnpDescriptionParser.parse(deep))
    }

    @Test
    fun `rejects external entity without reading it`() {
        val xml = """
            <!DOCTYPE root [
              <!ENTITY secret SYSTEM "file:///definitely-not-read">
            ]>
            <root><device><friendlyName>&secret;</friendlyName></device></root>
        """.trimIndent()

        assertNull(UpnpDescriptionParser.parse(xml))
    }

    private fun validDescription() = """
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
            <friendlyName>Living Room Router</friendlyName>
            <manufacturer>Example Networks</manufacturer>
            <manufacturerURL>https://example.invalid/vendor</manufacturerURL>
            <modelDescription>Router</modelDescription>
            <modelName>XR-1</modelName>
            <modelNumber>42</modelNumber>
            <UDN>uuid:root-device</UDN>
            <presentationURL>http://10.0.1.1/</presentationURL>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId>
                <SCPDURL>/ignored.xml</SCPDURL>
                <controlURL>/ignored/control</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()
}
