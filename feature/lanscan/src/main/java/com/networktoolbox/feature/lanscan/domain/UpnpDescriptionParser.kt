package com.networktoolbox.feature.lanscan.domain

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.xml.sax.EntityResolver
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler

/**
 * Streaming, bounded UPnP device-description parser. No URL in the document
 * is followed; only identity fields and service type/id are retained.
 */
object UpnpDescriptionParser {
    const val MAX_XML_BYTES: Int = 384 * 1024
    const val MAX_XML_DEPTH: Int = 64
    private const val MAX_TEXT_LENGTH = 2 * 1024
    private const val MAX_SERVICE_COUNT = 32

    fun parse(xml: String): UpnpDeviceDescription? =
        parse(xml.toByteArray(StandardCharsets.UTF_8))

    fun parse(xml: ByteArray): UpnpDeviceDescription? {
        if (xml.isEmpty() || xml.size > MAX_XML_BYTES) return null
        val handler = Handler()
        return try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isValidating = false
            }
            val reader = factory.newSAXParser().xmlReader.apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
                contentHandler = handler
                errorHandler = handler
            }
            reader.parse(InputSource(ByteArrayInputStream(xml)))
            handler.result()
        } catch (_: Exception) {
            null
        }
    }

    private class Handler : DefaultHandler() {
        private var depth = 0
        private var rootDeviceDepth: Int? = null
        private var embeddedDeviceDepth: Int? = null
        private var serviceDepth: Int? = null
        private var captureDepth: Int? = null
        private var captureName: String? = null
        private val captureText = StringBuilder()
        private var currentServiceType: String? = null
        private var currentServiceId: String? = null
        private val services = mutableListOf<UpnpServiceDescription>()
        private val fields = linkedMapOf<String, String>()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
            depth += 1
            if (depth > MAX_XML_DEPTH) throw IllegalArgumentException("UPnP XML is too deep.")
            val name = elementName(localName, qName)
            if (name == "device") {
                if (rootDeviceDepth == null) rootDeviceDepth = depth
                else if (embeddedDeviceDepth == null) embeddedDeviceDepth = depth
            }
            if (embeddedDeviceDepth == null && rootDeviceDepth != null) {
                if (name == "service" && depth > rootDeviceDepth!!) {
                    serviceDepth = depth
                    currentServiceType = null
                    currentServiceId = null
                }
                if (depth == rootDeviceDepth!! + 1 && name in DEVICE_FIELDS) {
                    beginCapture(name)
                } else if (
                    serviceDepth != null &&
                    depth == serviceDepth!! + 1 &&
                    name in SERVICE_FIELDS
                ) {
                    beginCapture(name)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (captureDepth != null && captureText.length <= MAX_TEXT_LENGTH) {
                captureText.append(ch, start, length)
                if (captureText.length > MAX_TEXT_LENGTH) {
                    throw IllegalArgumentException("UPnP XML text is too large.")
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            if (captureDepth == depth) {
                val value = captureText.toString().sanitizeXmlText()
                if (captureName in DEVICE_FIELDS && value.isNotBlank()) fields[captureName!!] = value
                if (captureName == "serviceType") currentServiceType = value.takeIf(String::isNotBlank)
                if (captureName == "serviceId") currentServiceId = value.takeIf(String::isNotBlank)
                captureDepth = null
                captureName = null
                captureText.clear()
            }
            if (serviceDepth == depth && name == "service") {
                if (!currentServiceType.isNullOrBlank() && services.size < MAX_SERVICE_COUNT) {
                    services += UpnpServiceDescription(
                        serviceType = currentServiceType!!,
                        serviceId = currentServiceId,
                    )
                }
                serviceDepth = null
                currentServiceType = null
                currentServiceId = null
            }
            if (embeddedDeviceDepth == depth && name == "device") embeddedDeviceDepth = null
            depth -= 1
        }

        fun result(): UpnpDeviceDescription = UpnpDeviceDescription(
            friendlyName = fields["friendlyName"],
            manufacturer = fields["manufacturer"],
            manufacturerUrl = fields["manufacturerURL"],
            modelDescription = fields["modelDescription"],
            modelName = fields["modelName"],
            modelNumber = fields["modelNumber"],
            deviceType = fields["deviceType"],
            udn = fields["UDN"],
            presentationUrl = fields["presentationURL"],
            services = services.toList(),
        )

        private fun beginCapture(name: String) {
            captureDepth = depth
            captureName = name
            captureText.clear()
        }
    }

    private fun elementName(localName: String?, qName: String?): String =
        (localName ?: qName.orEmpty()).substringAfter(':').trim()

    private fun String.sanitizeXmlText(): String =
        filter { character -> character.code >= 0x20 && character.code != 0x7F }
            .trim()
            .take(MAX_TEXT_LENGTH)

    private val DEVICE_FIELDS = setOf(
        "friendlyName",
        "manufacturer",
        "manufacturerURL",
        "modelDescription",
        "modelName",
        "modelNumber",
        "deviceType",
        "UDN",
        "presentationURL",
    )
    private val SERVICE_FIELDS = setOf("serviceType", "serviceId")
}
