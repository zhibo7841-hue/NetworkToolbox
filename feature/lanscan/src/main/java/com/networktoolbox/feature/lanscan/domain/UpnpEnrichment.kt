package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpObservation
import com.networktoolbox.feature.lanscan.domain.model.LanUpnpService
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SsdpDiscoveryRequest(
    val generation: Long,
    val networkIdentity: String,
    val connectionType: ConnectionType,
    val interfaceName: String?,
    val mxSeconds: Int = 1,
    val discoveryWindowMs: Long = DEFAULT_DISCOVERY_WINDOW_MS,
) {
    init {
        require(generation >= 0L) { "SSDP generation must not be negative." }
        require(networkIdentity.isNotBlank()) { "SSDP network identity is required." }
        require(connectionType == ConnectionType.WIFI || connectionType == ConnectionType.ETHERNET) {
            "SSDP is only supported on Wi-Fi or Ethernet."
        }
        require(mxSeconds in 1..2) { "SSDP MX must be one or two seconds." }
        require(discoveryWindowMs in 2_000L..4_000L) {
            "SSDP discovery window must be between two and four seconds."
        }
    }

    companion object {
        const val DEFAULT_DISCOVERY_WINDOW_MS: Long = 3_000L
    }
}

data class SsdpResponse(
    val sourceIp: String,
    val location: String? = null,
    val searchTarget: String? = null,
    val uniqueServiceName: String? = null,
    val server: String? = null,
    val cacheControl: String? = null,
    val extension: String? = null,
)

object SsdpMessageBuilder {
    fun mSearch(mxSeconds: Int): String {
        require(mxSeconds in 1..2) { "SSDP MX must be one or two seconds." }
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: $mxSeconds\r\n")
            append("ST: ssdp:all\r\n")
            append("\r\n")
        }
    }
}

/** Strict, bounded parser for one SSDP HTTP-like response. */
object SsdpResponseParser {
    const val MAX_PACKET_BYTES: Int = 16 * 1024
    private const val MAX_HEADER_LINE_LENGTH = 4 * 1024
    private const val MAX_HEADER_VALUE_LENGTH = 2 * 1024

    fun parse(sourceIp: String, rawResponse: String): SsdpResponse? {
        if (sourceIp.isBlank()) return null
        if (rawResponse.toByteArray(StandardCharsets.ISO_8859_1).size > MAX_PACKET_BYTES) return null
        val lines = rawResponse.split("\r\n", "\n")
        val status = lines.firstOrNull()?.trim().orEmpty()
        if (!status.matches(STATUS_LINE)) return null

        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            if (line.length > MAX_HEADER_LINE_LENGTH) continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase(Locale.US)
            if (name.isBlank() || name !in KNOWN_HEADERS || name in headers) continue
            val value = line.substring(separator + 1).sanitizeHeaderValue()
            if (value.isNotBlank()) headers[name] = value
        }
        return SsdpResponse(
            sourceIp = sourceIp.substringBefore('%').trim(),
            location = headers["location"],
            searchTarget = headers["st"],
            uniqueServiceName = headers["usn"],
            server = headers["server"],
            cacheControl = headers["cache-control"],
            extension = headers["ext"],
        )
    }

    private fun String.sanitizeHeaderValue(): String =
        filter { character -> character.code >= 0x20 && character.code != 0x7F }
            .trim()
            .take(MAX_HEADER_VALUE_LENGTH)

    private val STATUS_LINE = Regex("HTTP/1\\.[01]\\s+2\\d\\d(?:\\s.*)?", RegexOption.IGNORE_CASE)
    private val KNOWN_HEADERS = setOf(
        "location",
        "st",
        "usn",
        "server",
        "cache-control",
        "ext",
    )
}

/**
 * LOCATION is a hint from an untrusted LAN response. Phase 1 only permits a
 * literal HTTP(S) URL addressed to the responder itself.
 */
object UpnpLocationValidator {
    const val MAX_LOCATION_LENGTH: Int = 2 * 1024

    fun validate(location: String?, sourceIp: String): String? {
        return validateDetailed(location, sourceIp).normalizedLocation
    }

    fun validateDetailed(location: String?, sourceIp: String): UpnpLocationValidationResult {
        if (location.isNullOrBlank() || location.length > MAX_LOCATION_LENGTH) {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        }
        if (location.any { it.code < 0x20 || it.code == 0x7F || it.isWhitespace() }) {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        }
        val uri = runCatching { URI(location) }.getOrNull()
            ?: return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.UNSUPPORTED_SCHEME)
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        }
        if (uri.port !in -1..65_535) {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        }
        val normalizedSource = normalizeHost(sourceIp)
            ?: return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        val normalizedTarget = normalizeHost(uri.host)
            ?: return UpnpLocationValidationResult(null, UpnpFailureCategory.INVALID_LOCATION)
        if (normalizedSource != normalizedTarget) {
            return UpnpLocationValidationResult(null, UpnpFailureCategory.LOCATION_HOST_MISMATCH)
        }
        return UpnpLocationValidationResult(uri.toASCIIString())
    }

    private fun normalizeHost(value: String): String? {
        val host = value.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        if (host.isBlank()) return null
        val ipv4 = host.split('.')
        if (ipv4.size == 4 && ipv4.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }) {
            return ipv4.joinToString(".") { it.toInt().toString() }
        }
        if (host.contains(':') && host.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }) {
            return host.lowercase(Locale.US)
        }
        return null
    }
}

data class UpnpDescriptionRequest(
    val location: String,
    val sourceIp: String,
    val generation: Long,
    val networkIdentity: String,
    val connectionType: ConnectionType,
    val interfaceName: String?,
)

data class UpnpServiceDescription(
    val serviceType: String,
    val serviceId: String? = null,
)

data class UpnpDeviceDescription(
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val manufacturerUrl: String? = null,
    val modelDescription: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val deviceType: String? = null,
    val udn: String? = null,
    val presentationUrl: String? = null,
    val services: List<UpnpServiceDescription> = emptyList(),
) {
    val hasIdentity: Boolean
        get() = listOf(
            friendlyName,
            manufacturer,
            modelDescription,
            modelName,
            modelNumber,
            deviceType,
            udn,
        ).any { !it.isNullOrBlank() }
}

fun interface SsdpDiscovery {
    suspend fun discover(request: SsdpDiscoveryRequest): List<SsdpResponse>
}

fun interface UpnpDescriptionFetcher {
    suspend fun fetch(request: UpnpDescriptionRequest): UpnpDeviceDescription?
}

data class UpnpDeviceEnrichment(
    val ipAddress: String,
    val observation: LanUpnpObservation,
    val upnpDisplayNameCandidate: String?,
)

fun interface UpnpEnricher {
    suspend fun enrich(
        devices: List<LanDevice>,
        networkContext: NetworkContext,
        generation: Long,
        onResult: (UpnpDeviceEnrichment) -> Unit,
    )
}

object NoOpUpnpEnricher : UpnpEnricher {
    override suspend fun enrich(
        devices: List<LanDevice>,
        networkContext: NetworkContext,
        generation: Long,
        onResult: (UpnpDeviceEnrichment) -> Unit,
    ) = Unit
}

/** Bounded post-discovery SSDP + UPnP enrichment. It never creates devices. */
class DefaultUpnpEnricher(
    private val discovery: SsdpDiscovery,
    private val descriptionFetcher: UpnpDescriptionFetcher,
    private val discoveryWindowMs: Long = SsdpDiscoveryRequest.DEFAULT_DISCOVERY_WINDOW_MS,
    private val maxConcurrentFetches: Int = MAX_CONCURRENT_FETCHES,
    private val diagnosticLogger: UpnpDiagnosticLogger = NoOpUpnpDiagnosticLogger,
) : UpnpEnricher {
    init {
        require(discoveryWindowMs in 2_000L..4_000L) {
            "SSDP discovery window must be between two and four seconds."
        }
        require(maxConcurrentFetches in 1..MAX_CONCURRENT_FETCHES) {
            "UPnP description concurrency is outside the safety limit."
        }
    }

    override suspend fun enrich(
        devices: List<LanDevice>,
        networkContext: NetworkContext,
        generation: Long,
        onResult: (UpnpDeviceEnrichment) -> Unit,
    ) = coroutineScope {
        if (!networkContext.isUpnpEligible() || devices.isEmpty()) return@coroutineScope
        val knownIps = devices.map(LanDevice::ipAddress).toSet()
        val request = SsdpDiscoveryRequest(
            generation = generation,
            networkIdentity = networkContext.upnpNetworkIdentity(),
            connectionType = networkContext.connectionType,
            interfaceName = networkContext.interfaceName,
            discoveryWindowMs = discoveryWindowMs,
        )
        val responses = try {
            discovery.discover(request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        currentCoroutineContext().ensureActive()

        val candidates = responses
            .asSequence()
            .mapNotNull { response ->
                val ip = response.sourceIp.substringBefore('%').trim()
                if (ip !in knownIps) {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.UPNP_DEVICE_IGNORED,
                            generation = generation,
                            sourceIp = ip,
                            location = safeUpnpLocationForLog(response.location),
                            failureCategory = UpnpFailureCategory.DEVICE_IP_NOT_IN_SCAN_RESULTS,
                        ),
                    )
                    return@mapNotNull null
                }
                val validation = UpnpLocationValidator.validateDetailed(response.location, ip)
                val location = validation.normalizedLocation
                if (location == null) {
                    log(
                        UpnpDiagnosticEvent(
                            type = UpnpDiagnosticEventType.UPNP_LOCATION_REJECTED,
                            generation = generation,
                            sourceIp = ip,
                            location = safeUpnpLocationForLog(response.location),
                            failureCategory = validation.failureCategory ?: UpnpFailureCategory.INVALID_LOCATION,
                        ),
                    )
                    return@mapNotNull null
                }
                response.copy(sourceIp = ip, location = location)
            }
            .distinctBy { response ->
                listOf(
                    response.sourceIp,
                    response.location?.lowercase(Locale.US).orEmpty(),
                ).joinToString("\u0000")
            }
            .onEach { response ->
                log(
                    UpnpDiagnosticEvent(
                        type = UpnpDiagnosticEventType.UPNP_LOCATION_ACCEPTED,
                        generation = generation,
                        sourceIp = response.sourceIp,
                        location = safeUpnpLocationForLog(response.location),
                    ),
                )
            }
            .take(MAX_DESCRIPTION_FETCHES)
            .toList()
        if (candidates.isEmpty()) return@coroutineScope

        val fetchSlots = Semaphore(maxConcurrentFetches)
        val observations = candidates.map { response ->
            async {
                fetchSlots.withPermit {
                    currentCoroutineContext().ensureActive()
                    val description = try {
                        descriptionFetcher.fetch(
                            UpnpDescriptionRequest(
                                location = requireNotNull(response.location),
                                sourceIp = response.sourceIp,
                                generation = generation,
                                networkIdentity = request.networkIdentity,
                                connectionType = request.connectionType,
                                interfaceName = request.interfaceName,
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (description?.hasIdentity != true) {
                        log(
                            UpnpDiagnosticEvent(
                                type = UpnpDiagnosticEventType.UPNP_DEVICE_IGNORED,
                                generation = generation,
                                sourceIp = response.sourceIp,
                                location = safeUpnpLocationForLog(response.location),
                                failureCategory = UpnpFailureCategory.NO_IDENTITY_FIELDS,
                            ),
                        )
                        null
                    } else {
                        FetchedDescription(response, description)
                    }
                }
            }
        }.awaitAll().filterNotNull()

        currentCoroutineContext().ensureActive()
        observations.groupBy(FetchedDescription::sourceIp).forEach { (ipAddress, items) ->
            currentCoroutineContext().ensureActive()
            val merged = items.map(FetchedDescription::description).reduce(::mergeDescriptions)
            val first = items.first().response
            val observation = merged.toObservation(
                usn = first.uniqueServiceName,
                server = first.server,
                generation = generation,
                networkIdentity = request.networkIdentity,
            )
            onResult(
                UpnpDeviceEnrichment(
                    ipAddress = ipAddress,
                    observation = observation,
                    upnpDisplayNameCandidate = observation.friendlyName,
                ),
            )
            log(
                UpnpDiagnosticEvent(
                    type = UpnpDiagnosticEventType.UPNP_DEVICE_ASSOCIATED,
                    generation = generation,
                    sourceIp = ipAddress,
                    networkIdentity = request.networkIdentity,
                ),
            )
        }
    }

    private fun log(event: UpnpDiagnosticEvent) {
        runCatching { diagnosticLogger.log(event) }
    }

    private data class FetchedDescription(
        val response: SsdpResponse,
        val description: UpnpDeviceDescription,
    ) {
        val sourceIp: String get() = response.sourceIp
    }

    private fun mergeDescriptions(
        first: UpnpDeviceDescription,
        second: UpnpDeviceDescription,
    ): UpnpDeviceDescription = UpnpDeviceDescription(
        friendlyName = first.friendlyName ?: second.friendlyName,
        manufacturer = first.manufacturer ?: second.manufacturer,
        manufacturerUrl = first.manufacturerUrl ?: second.manufacturerUrl,
        modelDescription = first.modelDescription ?: second.modelDescription,
        modelName = first.modelName ?: second.modelName,
        modelNumber = first.modelNumber ?: second.modelNumber,
        deviceType = first.deviceType ?: second.deviceType,
        udn = first.udn ?: second.udn,
        presentationUrl = first.presentationUrl ?: second.presentationUrl,
        services = (first.services + second.services).distinctBy {
            it.serviceType.lowercase(Locale.US) to it.serviceId?.lowercase(Locale.US)
        },
    )

    private fun UpnpDeviceDescription.toObservation(
        usn: String?,
        server: String?,
        generation: Long,
        networkIdentity: String,
    ): LanUpnpObservation = LanUpnpObservation(
        friendlyName = friendlyName,
        manufacturer = manufacturer,
        manufacturerUrl = manufacturerUrl,
        modelDescription = modelDescription,
        modelName = modelName,
        modelNumber = modelNumber,
        deviceType = deviceType,
        udn = udn,
        presentationUrl = presentationUrl,
        services = services.map { LanUpnpService(it.serviceType, it.serviceId) },
        usn = usn,
        server = server,
        observedAt = System.currentTimeMillis(),
        generation = generation,
        networkIdentity = networkIdentity,
        source = LanDeviceNameSource.UPNP,
    )

    companion object {
        const val MAX_CONCURRENT_FETCHES: Int = 2
        const val MAX_DESCRIPTION_FETCHES: Int = 32
    }
}

private fun NetworkContext.isUpnpEligible(): Boolean =
    activeNetworkAvailable == true && vpnActive != true && connectionType in setOf(
        ConnectionType.WIFI,
        ConnectionType.ETHERNET,
    )

internal fun NetworkContext.upnpNetworkIdentity(): String = listOf(
    connectionType.name,
    interfaceName.orEmpty(),
    ipv4Address.orEmpty(),
    ipv4PrefixLength?.toString().orEmpty(),
    gateway.orEmpty(),
).joinToString(separator = "|")
