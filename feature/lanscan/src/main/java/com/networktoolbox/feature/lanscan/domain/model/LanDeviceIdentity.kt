package com.networktoolbox.feature.lanscan.domain.model

import java.util.Locale

/** The source of a value that is shown as part of a device identity. */
enum class LanIdentitySource {
    UPNP,
    MDNS,
    REVERSE_DNS,
    IP_FALLBACK,
}

data class LanSourcedValue(
    val value: String,
    val source: LanIdentitySource,
)

/**
 * A normalized view of optional identity information.
 *
 * The raw observations remain on [LanDevice]. This value is deliberately
 * computed from those observations so that an enrichment callback cannot make
 * the UI depend on callback arrival order or keep a second mutable identity
 * cache in sync.
 */
data class LanDeviceIdentity(
    val displayName: LanSourcedValue,
    val hostname: LanSourcedValue? = null,
    val manufacturer: LanSourcedValue? = null,
    val modelName: LanSourcedValue? = null,
    val modelNumber: LanSourcedValue? = null,
    val modelDescription: LanSourcedValue? = null,
    val deviceType: LanSourcedValue? = null,
    val services: List<LanIdentityService> = emptyList(),
    val sources: Set<LanIdentitySource> = setOf(LanIdentitySource.IP_FALLBACK),
)

data class LanIdentityService(
    val source: LanIdentitySource,
    val serviceName: String? = null,
    val serviceType: String,
    val hostname: String? = null,
    val port: Int? = null,
)

/**
 * Pure aggregation of the identity observations already collected by the LAN
 * scanner. It does not perform DNS, mDNS, UPnP, or any other network work.
 */
object LanDeviceIdentityAggregator {
    private const val MAX_IDENTITY_TEXT_LENGTH = 128
    private val genericNames = setOf(
        "device",
        "unknown",
        "upnp device",
        "router",
        "localhost",
    )

    fun aggregate(device: LanDevice): LanDeviceIdentity {
        val upnpObservations = device.upnpObservations
        val mdnsObservations = device.mdnsObservations

        val upnpDisplayName = bestMeaningful(upnpObservations.mapNotNull { it.friendlyName })
            ?: meaningful(device.upnpDisplayNameCandidate)
        val mdnsServiceName = bestMeaningful(
            mdnsObservations.map(LanMdnsObservation::serviceName),
        ) ?: meaningful(device.mdnsDisplayNameCandidate)
        val mdnsHostname = bestMeaningful(
            mdnsObservations.mapNotNull(LanMdnsObservation::hostname),
        )
        val hostNameSource = when (device.hostNameSource) {
            LanDeviceNameSource.MDNS -> LanIdentitySource.MDNS
            LanDeviceNameSource.UPNP -> LanIdentitySource.UPNP
            LanDeviceNameSource.REVERSE_DNS, null -> LanIdentitySource.REVERSE_DNS
        }
        val hostNameCandidate = device.hostName
            ?.let(::meaningful)
            ?.let { LanSourcedValue(it, hostNameSource) }

        val displayName = upnpDisplayName?.let { LanSourcedValue(it, LanIdentitySource.UPNP) }
            ?: mdnsServiceName?.let { LanSourcedValue(it, LanIdentitySource.MDNS) }
            ?: mdnsHostname?.let { LanSourcedValue(it, LanIdentitySource.MDNS) }
            ?: hostNameCandidate
            ?: LanSourcedValue(device.ipAddress, LanIdentitySource.IP_FALLBACK)

        val hostname = hostNameCandidate
            ?.takeIf { it.source == LanIdentitySource.REVERSE_DNS }
            ?: mdnsHostname?.let { LanSourcedValue(it, LanIdentitySource.MDNS) }

        val manufacturer = bestSourced(
            upnpObservations.mapNotNull { it.manufacturer },
            LanIdentitySource.UPNP,
        )
        val modelName = bestSourced(
            upnpObservations.mapNotNull { it.modelName },
            LanIdentitySource.UPNP,
        )
        val modelNumber = bestSourced(
            upnpObservations.mapNotNull { it.modelNumber },
            LanIdentitySource.UPNP,
        )
        val modelDescription = bestSourced(
            upnpObservations.mapNotNull { it.modelDescription },
            LanIdentitySource.UPNP,
        )
        val deviceType = bestSourced(
            upnpObservations.mapNotNull { it.deviceType },
            LanIdentitySource.UPNP,
        )
        val services = aggregateServices(mdnsObservations, upnpObservations)
        val sources = buildSet {
            add(displayName.source)
            listOfNotNull(
                hostname,
                manufacturer,
                modelName,
                modelNumber,
                modelDescription,
                deviceType,
            ).forEach { add(it.source) }
            services.forEach { add(it.source) }
        }

        return LanDeviceIdentity(
            displayName = displayName,
            hostname = hostname,
            manufacturer = manufacturer,
            modelName = modelName,
            modelNumber = modelNumber,
            modelDescription = modelDescription,
            deviceType = deviceType,
            services = services,
            sources = sources,
        )
    }

    private fun bestSourced(values: List<String>, source: LanIdentitySource): LanSourcedValue? =
        bestMeaningful(values)?.let { LanSourcedValue(it, source) }

    private fun bestMeaningful(values: List<String>): String? = values
        .asSequence()
        .mapNotNull(::meaningful)
        .sortedWith(compareBy<String>({ it.lowercase(Locale.ROOT) }, { it }))
        .distinctBy { it.lowercase(Locale.ROOT) }
        .firstOrNull()

    private fun meaningful(value: String?): String? {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.length > MAX_IDENTITY_TEXT_LENGTH) return null
        if (candidate.any(Char::isISOControl)) return null
        if (genericNames.contains(candidate.lowercase(Locale.ROOT))) return null
        if (isIpLiteral(candidate)) return null
        return candidate
    }

    private fun isIpLiteral(value: String): Boolean {
        val ipv4Parts = value.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
            }
        ) {
            return true
        }
        return value.contains(':') && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '%' || it == '.' }
    }

    private fun aggregateServices(
        mdnsObservations: List<LanMdnsObservation>,
        upnpObservations: List<LanUpnpObservation>,
    ): List<LanIdentityService> {
        val mdnsServices = mdnsObservations.map {
            LanIdentityService(
                source = LanIdentitySource.MDNS,
                serviceName = it.serviceName,
                serviceType = it.serviceType,
                hostname = it.hostname,
                port = it.port,
            )
        }
        val upnpServices = upnpObservations.flatMap { observation ->
            observation.services.map { service ->
                LanIdentityService(
                    source = LanIdentitySource.UPNP,
                    serviceName = service.serviceId,
                    serviceType = service.serviceType,
                )
            }
        }
        return (mdnsServices + upnpServices)
            .distinctBy {
                listOf(
                    it.source.name,
                    it.serviceName.orEmpty(),
                    it.serviceType,
                    it.hostname.orEmpty(),
                    it.port?.toString().orEmpty(),
                ).joinToString("\u0000")
            }
            .sortedWith(
                compareBy<LanIdentityService>(
                    { it.source.name },
                    { it.serviceType },
                    { it.serviceName.orEmpty() },
                    { it.hostname.orEmpty() },
                    { it.port ?: -1 },
                ),
            )
    }
}

val LanDevice.identity: LanDeviceIdentity
    get() = LanDeviceIdentityAggregator.aggregate(this)
