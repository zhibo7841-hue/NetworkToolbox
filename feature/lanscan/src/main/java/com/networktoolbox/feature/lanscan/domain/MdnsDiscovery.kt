package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanMdnsObservation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class MdnsDiscoveryRequest(
    val generation: Long,
    val networkIdentity: String,
    val connectionType: ConnectionType,
    val serviceTypes: List<String>,
    val discoveryWindowMs: Long = DEFAULT_DISCOVERY_WINDOW_MS,
    val maxConcurrentServiceTypes: Int = DEFAULT_MAX_CONCURRENT_SERVICE_TYPES,
) {
    init {
        require(generation >= 0L) { "mDNS generation must not be negative." }
        require(networkIdentity.isNotBlank()) { "mDNS network identity is required." }
        require(serviceTypes.isNotEmpty()) { "At least one mDNS service type is required." }
        require(serviceTypes.size <= maxConcurrentServiceTypes) {
            "mDNS service types must fit within the bounded discovery window."
        }
        require(discoveryWindowMs > 0L) { "mDNS discovery window must be positive." }
        require(maxConcurrentServiceTypes in 1..MAX_CONCURRENT_SERVICE_TYPES) {
            "mDNS service-type concurrency must be between 1 and $MAX_CONCURRENT_SERVICE_TYPES."
        }
    }

    companion object {
        const val DEFAULT_DISCOVERY_WINDOW_MS: Long = 4_000L
        const val DEFAULT_MAX_CONCURRENT_SERVICE_TYPES: Int = 3
        const val MAX_CONCURRENT_SERVICE_TYPES: Int = 3
    }
}

object MdnsServiceTypes {
    /** Small, reviewed allow-list; NsdManager does not enumerate every Bonjour type. */
    val DEFAULT: List<String> = listOf(
        "_http._tcp",
        "_ipp._tcp",
        "_smb._tcp",
    )
}

data class MdnsServiceKey(
    val serviceName: String,
    val serviceType: String,
)

data class MdnsObservation(
    val serviceName: String,
    val serviceType: String,
    val hostname: String? = null,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val port: Int? = null,
    val txtAttributes: Map<String, String> = emptyMap(),
    val observedAt: Long,
    val generation: Long,
    val networkIdentity: String,
    val source: LanDeviceNameSource = LanDeviceNameSource.MDNS,
) {
    val serviceKey: MdnsServiceKey
        get() = MdnsServiceKey(serviceName = serviceName, serviceType = serviceType)
}

fun MdnsObservation.toLanMdnsObservation(): LanMdnsObservation = LanMdnsObservation(
    serviceName = serviceName,
    serviceType = serviceType,
    hostname = hostname,
    ipv4Addresses = ipv4Addresses,
    ipv6Addresses = ipv6Addresses,
    port = port,
    txtAttributes = txtAttributes,
    observedAt = observedAt,
)

sealed interface MdnsDiscoveryEvent {
    data class DiscoveryStarted(val serviceType: String) : MdnsDiscoveryEvent

    data class ServiceFound(
        val serviceName: String,
        val serviceType: String,
    ) : MdnsDiscoveryEvent

    data class ServiceResolved(val observation: MdnsObservation) : MdnsDiscoveryEvent

    data class ServiceLost(
        val serviceName: String,
        val serviceType: String,
    ) : MdnsDiscoveryEvent

    data class ResolveFailed(
        val serviceName: String,
        val serviceType: String,
        val errorCode: Int,
    ) : MdnsDiscoveryEvent

    data class DiscoveryStartFailed(
        val serviceType: String,
        val errorCode: Int,
    ) : MdnsDiscoveryEvent

    data class DiscoveryStopped(val serviceType: String) : MdnsDiscoveryEvent

    data class DiscoveryStopFailed(
        val serviceType: String,
        val errorCode: Int,
    ) : MdnsDiscoveryEvent
}

fun interface MdnsDiscovery {
    fun start(
        request: MdnsDiscoveryRequest,
        onEvent: (MdnsDiscoveryEvent) -> Unit,
    ): MdnsDiscoverySession
}

fun interface MdnsDiscoverySession {
    fun stop()
}

data class MdnsDeviceEnrichment(
    val ipAddress: String,
    val observation: MdnsObservation,
    val mdnsDisplayNameCandidate: String?,
)

fun interface MdnsEnricher {
    suspend fun enrich(
        devices: List<LanDevice>,
        networkContext: NetworkContext,
        generation: Long,
        onResult: (MdnsDeviceEnrichment) -> Unit,
    )
}

/**
 * Runs one bounded mDNS session after discovery and associates only resolved
 * IPv4 addresses already present in the LAN Scanner result.
 */
class DefaultMdnsEnricher(
    private val discovery: MdnsDiscovery,
    private val serviceTypes: List<String> = MdnsServiceTypes.DEFAULT,
    private val discoveryWindowMs: Long = MdnsDiscoveryRequest.DEFAULT_DISCOVERY_WINDOW_MS,
    private val maxConcurrentServiceTypes: Int = MdnsDiscoveryRequest.DEFAULT_MAX_CONCURRENT_SERVICE_TYPES,
) : MdnsEnricher {
    init {
        require(serviceTypes.isNotEmpty()) { "At least one mDNS service type is required." }
        require(serviceTypes.size <= maxConcurrentServiceTypes) {
            "The configured mDNS service list exceeds the bounded concurrency."
        }
        require(discoveryWindowMs > 0L) { "mDNS discovery window must be positive." }
        require(maxConcurrentServiceTypes in 1..MdnsDiscoveryRequest.MAX_CONCURRENT_SERVICE_TYPES) {
            "mDNS service-type concurrency is outside the safety limit."
        }
    }

    override suspend fun enrich(
        devices: List<LanDevice>,
        networkContext: NetworkContext,
        generation: Long,
        onResult: (MdnsDeviceEnrichment) -> Unit,
    ) {
        if (!networkContext.isMdnsEligible() || devices.isEmpty()) return

        val discoveredIps = devices.map(LanDevice::ipAddress).toSet()
        val request = MdnsDiscoveryRequest(
            generation = generation,
            networkIdentity = networkContext.mdnsNetworkIdentity(),
            connectionType = networkContext.connectionType,
            serviceTypes = serviceTypes,
            discoveryWindowMs = discoveryWindowMs,
            maxConcurrentServiceTypes = maxConcurrentServiceTypes,
        )
        val accepting = AtomicBoolean(true)
        val observedServiceAddresses = ConcurrentHashMap.newKeySet<Pair<MdnsServiceKey, String>>()
        val session = try {
            discovery.start(request) { event ->
                if (accepting.get()) {
                    val resolved = event as? MdnsDiscoveryEvent.ServiceResolved
                    if (resolved != null) {
                        val observation = resolved.observation.sanitized()
                        if (
                            observation.generation == request.generation &&
                                observation.networkIdentity == request.networkIdentity &&
                                observation.serviceName.isNotBlank() &&
                                observation.serviceType.isNotBlank()
                        ) {
                            observation.ipv4Addresses
                                .firstOrNull(discoveredIps::contains)
                                ?.let { ipAddress ->
                                    if (observedServiceAddresses.add(observation.serviceKey to ipAddress)) {
                                        onResult(
                                            MdnsDeviceEnrichment(
                                                ipAddress = ipAddress,
                                                observation = observation,
                                                mdnsDisplayNameCandidate = observation.displayNameCandidate(),
                                            ),
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }

        try {
            delay(discoveryWindowMs)
        } finally {
            accepting.set(false)
            // A framework stop failure must not turn an optional enrichment
            // into a LAN scan failure.
            runCatching { session.stop() }
        }
    }
}

private fun NetworkContext.isMdnsEligible(): Boolean =
    activeNetworkAvailable == true && vpnActive != true && connectionType in setOf(
        ConnectionType.WIFI,
        ConnectionType.ETHERNET,
    )

private fun NetworkContext.mdnsNetworkIdentity(): String = listOf(
    connectionType.name,
    interfaceName.orEmpty(),
    ipv4Address.orEmpty(),
    ipv4PrefixLength?.toString().orEmpty(),
    gateway.orEmpty(),
).joinToString(separator = "|")

private fun MdnsObservation.sanitized(): MdnsObservation {
    val safeServiceName = serviceName.sanitizeMdnsText(MAX_SERVICE_NAME_LENGTH)
    val safeServiceType = serviceType.sanitizeMdnsText(MAX_SERVICE_TYPE_LENGTH)
    val safeHostname = hostname?.sanitizeMdnsText(MAX_HOSTNAME_LENGTH)
    val safeTxt = txtAttributes.entries
        .asSequence()
        .mapNotNull { (key, value) ->
            val safeKey = key.sanitizeMdnsText(MAX_TXT_KEY_LENGTH)
            val safeValue = value.sanitizeMdnsText(MAX_TXT_VALUE_LENGTH)
            if (safeKey.isBlank() || safeValue.isBlank()) null else safeKey to safeValue
        }
        .take(MAX_TXT_ATTRIBUTE_COUNT)
        .toMap()
        .let { attributes ->
            var size = 0
            attributes.filter { (key, value) ->
                size += key.length + value.length
                size <= MAX_TXT_TOTAL_LENGTH
            }
        }
    return copy(
        serviceName = safeServiceName,
        serviceType = safeServiceType,
        hostname = safeHostname,
        ipv4Addresses = ipv4Addresses.map(String::trim).filter(::isValidIpv4).distinct(),
        ipv6Addresses = ipv6Addresses.map(String::trim).filter(String::isNotBlank).distinct(),
        port = port?.takeIf { it in 1..65_535 },
        txtAttributes = safeTxt,
    )
}

private fun MdnsObservation.displayNameCandidate(): String? =
    serviceName
        .takeIf { it.isNotBlank() && !it.startsWith("_") }
        ?.takeIf {
            !it.contains("._tcp", ignoreCase = true) &&
                !it.contains("._udp", ignoreCase = true)
        }

private fun String.sanitizeMdnsText(maxLength: Int): String =
    filter { character -> character.code >= 0x20 && character.code != 0x7F }
        .trim()
        .take(maxLength)

private fun isValidIpv4(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

private const val MAX_SERVICE_NAME_LENGTH = 128
private const val MAX_SERVICE_TYPE_LENGTH = 64
private const val MAX_HOSTNAME_LENGTH = 255
private const val MAX_TXT_ATTRIBUTE_COUNT = 16
private const val MAX_TXT_KEY_LENGTH = 64
private const val MAX_TXT_VALUE_LENGTH = 256
private const val MAX_TXT_TOTAL_LENGTH = 1_024
