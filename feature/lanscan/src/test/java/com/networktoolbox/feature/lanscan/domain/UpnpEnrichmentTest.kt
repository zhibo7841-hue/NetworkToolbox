package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceNameSource
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpEnrichmentTest {
    @Test
    fun `associates only known devices and merges duplicate locations`() = runBlocking {
        val fetchCount = AtomicInteger(0)
        val discovery = SsdpDiscovery {
            listOf(
                response("10.0.1.10", "root.xml", "uuid:root"),
                response("10.0.1.10", "root.xml", "uuid:device"),
                response("10.0.1.10", "services.xml", "uuid:service"),
                response("10.0.1.99", "unknown.xml", "uuid:unknown"),
            )
        }
        val fetcher = UpnpDescriptionFetcher { request ->
            fetchCount.incrementAndGet()
            if (request.location.endsWith("root.xml")) {
                UpnpDeviceDescription(friendlyName = "Router", manufacturer = "Example")
            } else {
                UpnpDeviceDescription(
                    modelName = "Model-1",
                    services = listOf(
                        UpnpServiceDescription(
                            serviceType = "urn:service:one",
                            serviceId = "urn:id:one",
                        ),
                    ),
                )
            }
        }
        val results = mutableListOf<UpnpDeviceEnrichment>()

        DefaultUpnpEnricher(discovery, fetcher).enrich(
            devices = listOf(device("10.0.1.10")),
            networkContext = wifiContext(),
            generation = 7L,
            onResult = results::add,
        )

        assertEquals(2, fetchCount.get())
        assertEquals(1, results.size)
        assertEquals("10.0.1.10", results.single().ipAddress)
        assertEquals("Router", results.single().observation.friendlyName)
        assertEquals("Example", results.single().observation.manufacturer)
        assertEquals("Model-1", results.single().observation.modelName)
        assertEquals(1, results.single().observation.services.size)
        assertEquals(LanDeviceNameSource.UPNP, results.single().observation.source)
        assertEquals(7L, results.single().observation.generation)
        assertEquals("WIFI|wlan0|10.0.1.20|24|10.0.1.1", results.single().observation.networkIdentity)
    }

    @Test
    fun `does not run on cellular or vpn networks`() = runBlocking {
        var called = false
        val discovery = SsdpDiscovery { called = true; emptyList() }
        val fetcher = UpnpDescriptionFetcher { error("description fetch must not run") }

        DefaultUpnpEnricher(discovery, fetcher).enrich(
            devices = listOf(device("10.0.1.10")),
            networkContext = wifiContext().copy(connectionType = ConnectionType.CELLULAR),
            generation = 1L,
            onResult = {},
        )
        assertTrue(!called)

        DefaultUpnpEnricher(discovery, fetcher).enrich(
            devices = listOf(device("10.0.1.10")),
            networkContext = wifiContext().copy(vpnActive = true),
            generation = 2L,
            onResult = {},
        )
        assertTrue(!called)
    }

    @Test
    fun `cancellation stops enrichment without emitting late results`() = runBlocking {
        val job: Job = launch {
            DefaultUpnpEnricher(
                discovery = SsdpDiscovery { awaitCancellation() },
                descriptionFetcher = UpnpDescriptionFetcher { error("not reached") },
            ).enrich(
                devices = listOf(device("10.0.1.10")),
                networkContext = wifiContext(),
                generation = 3L,
                onResult = { error("late result must be ignored") },
            )
        }

        delay(20)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `keeps description fetches bounded`() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val responses = (1..8).map { index ->
            response("10.0.1.10", "root-$index.xml", "uuid:$index")
        }
        val fetcher = UpnpDescriptionFetcher {
            val now = active.incrementAndGet()
            maximum.updateAndGet { current -> maxOf(current, now) }
            delay(10)
            active.decrementAndGet()
            UpnpDeviceDescription(modelName = "Model")
        }

        DefaultUpnpEnricher(
            discovery = SsdpDiscovery { responses },
            descriptionFetcher = fetcher,
        ).enrich(
            devices = listOf(device("10.0.1.10")),
            networkContext = wifiContext(),
            generation = 4L,
            onResult = {},
        )

        assertTrue(maximum.get() <= 2)
    }

    private fun response(ip: String, path: String, usn: String) = SsdpResponse(
        sourceIp = ip,
        location = "http://$ip/$path",
        searchTarget = "upnp:rootdevice",
        uniqueServiceName = usn,
        server = "Test/1.0",
    )

    private fun device(ip: String) = LanDevice(
        ipAddress = ip,
        isLocalDevice = false,
        isGateway = false,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 1L,
    )

    private fun wifiContext() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.20",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = 3,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )
}
