package com.networktoolbox.feature.lanscan.domain

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsDiscoveryTest {
    @Test
    fun `resolved service only enriches matching discovered ipv4 and deduplicates`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val results = mutableListOf<MdnsDeviceEnrichment>()
        val device = device("10.0.1.50")
        val secondDevice = device("10.0.1.51")
        val job = launch {
            DefaultMdnsEnricher(
                discovery = discovery,
                discoveryWindowMs = 4_000L,
            ).enrich(
                devices = listOf(device, secondDevice),
                networkContext = wifiContext(),
                generation = 4L,
                onResult = results::add,
            )
        }

        runCurrent()
        val observation = observation(
            serviceName = "Home Printer",
            serviceType = "_ipp._tcp",
            ipv4Addresses = listOf("10.0.1.50", "10.0.1.51"),
            generation = 4L,
        )
        discovery.emit(MdnsDiscoveryEvent.ServiceResolved(observation))
        discovery.emit(MdnsDiscoveryEvent.ServiceResolved(observation))
        discovery.emit(
            MdnsDiscoveryEvent.ServiceResolved(
                observation.copy(ipv4Addresses = listOf("10.0.1.99")),
            ),
        )
        discovery.emit(
            MdnsDiscoveryEvent.ServiceResolved(
                observation.copy(ipv4Addresses = emptyList(), ipv6Addresses = listOf("fe80::1")),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("10.0.1.50", results.single().ipAddress)
        assertEquals("Home Printer", results.single().mdnsDisplayNameCandidate)
        assertEquals(1, discovery.stopCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `default service list is bounded and stopped at the shared window`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val job = launch {
            DefaultMdnsEnricher(discovery).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 1L,
                onResult = {},
            )
        }

        runCurrent()
        assertEquals(
            listOf("_http._tcp", "_ipp._tcp", "_smb._tcp"),
            discovery.request.serviceTypes,
        )
        assertEquals(3, discovery.request.maxConcurrentServiceTypes)
        assertTrue(job.isActive)

        advanceUntilIdle()

        assertEquals(1, discovery.stopCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `session stop is idempotent after timeout`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 2L,
                onResult = {},
            )
        }

        runCurrent()
        advanceUntilIdle()
        discovery.session.stop()
        discovery.session.stop()

        assertEquals(1, discovery.stopCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `cancellation racing with timeout closes session once`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 3L,
                onResult = {},
            )
        }

        runCurrent()
        job.cancel()
        job.join()
        advanceUntilIdle()

        assertEquals(1, discovery.stopCount)
    }

    @Test
    fun `stop callback after timeout is ignored`() = runTest {
        val discovery = FakeMdnsDiscovery(
            stopEvents = listOf(
                MdnsDiscoveryEvent.DiscoveryStopped("_http._tcp"),
                MdnsDiscoveryEvent.DiscoveryStopped("_ipp._tcp"),
                MdnsDiscoveryEvent.DiscoveryStopped("_smb._tcp"),
            ),
        )
        val results = mutableListOf<MdnsDeviceEnrichment>()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 4L,
                onResult = results::add,
            )
        }

        runCurrent()
        advanceUntilIdle()

        assertEquals(1, discovery.stopCount)
        assertTrue(results.isEmpty())
        assertTrue(job.isCompleted)
    }

    @Test
    fun `duplicate discovery stopped events are harmless`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 5L,
                onResult = {},
            )
        }

        runCurrent()
        discovery.emit(MdnsDiscoveryEvent.DiscoveryStopped("_http._tcp"))
        discovery.emit(MdnsDiscoveryEvent.DiscoveryStopped("_http._tcp"))
        advanceUntilIdle()

        assertEquals(1, discovery.stopCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `stop failure event is non fatal`() = runTest {
        val discovery = FakeMdnsDiscovery(
            stopEvents = listOf(MdnsDiscoveryEvent.DiscoveryStopFailed("_http._tcp", 1)),
        )

        DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
            devices = listOf(device("10.0.1.50")),
            networkContext = wifiContext(),
            generation = 6L,
            onResult = {},
        )

        assertEquals(1, discovery.stopCount)
    }

    @Test
    fun `start failure event is non fatal`() = runTest {
        val discovery = FakeMdnsDiscovery(
            startEvents = listOf(MdnsDiscoveryEvent.DiscoveryStartFailed("_http._tcp", 1)),
        )

        DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
            devices = listOf(device("10.0.1.50")),
            networkContext = wifiContext(),
            generation = 7L,
            onResult = {},
        )

        assertEquals(1, discovery.stopCount)
    }

    @Test
    fun `three service types stop through one shared session`() = runTest {
        val discovery = FakeMdnsDiscovery(
            stopEvents = MdnsServiceTypes.DEFAULT.map(MdnsDiscoveryEvent::DiscoveryStopped),
        )
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 8L,
                onResult = {},
            )
        }

        runCurrent()
        advanceUntilIdle()

        assertEquals(MdnsServiceTypes.DEFAULT, discovery.request.serviceTypes)
        assertEquals(1, discovery.stopCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `late event after window and stale network event are ignored`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val results = mutableListOf<MdnsDeviceEnrichment>()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 7L,
                onResult = results::add,
            )
        }

        runCurrent()
        advanceUntilIdle()
        discovery.emit(
            MdnsDiscoveryEvent.ServiceResolved(
                observation(
                    serviceName = "Late",
                    serviceType = "_http._tcp",
                    ipv4Addresses = listOf("10.0.1.50"),
                    generation = 7L,
                ),
            ),
        )
        discovery.emit(
            MdnsDiscoveryEvent.ServiceResolved(
                observation(
                    serviceName = "Old Network",
                    serviceType = "_http._tcp",
                    ipv4Addresses = listOf("10.0.1.50"),
                    generation = 6L,
                ),
            ),
        )

        assertEquals(0, results.size)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `cellular and vpn contexts do not start mdns`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val enricher = DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L)

        enricher.enrich(listOf(device("10.0.1.50")), wifiContext(ConnectionType.CELLULAR), 1L) {}
        enricher.enrich(
            listOf(device("10.0.1.50")),
            wifiContext().copy(vpnActive = true),
            2L,
        ) {}

        assertEquals(0, discovery.startCount)
        assertEquals(0, discovery.stopCount)
    }

    @Test
    fun `discovery start failure does not fail the scan`() = runTest {
        val discovery = FakeMdnsDiscovery(startFailure = IllegalStateException("framework unavailable"))

        DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
            devices = listOf(device("10.0.1.50")),
            networkContext = wifiContext(),
            generation = 1L,
            onResult = {},
        )

        assertEquals(1, discovery.startCount)
        assertEquals(0, discovery.stopCount)
    }

    @Test
    fun `discovery stop failure does not fail the scan`() = runTest {
        val discovery = FakeMdnsDiscovery(stopFailure = IllegalStateException("stop failed"))

        DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
            devices = listOf(device("10.0.1.50")),
            networkContext = wifiContext(),
            generation = 1L,
            onResult = {},
        )

        assertEquals(1, discovery.stopCount)
    }

    @Test
    fun `untrusted observation text and txt attributes are bounded`() = runTest {
        val discovery = FakeMdnsDiscovery()
        val results = mutableListOf<MdnsDeviceEnrichment>()
        val job = launch {
            DefaultMdnsEnricher(discovery, discoveryWindowMs = 1_000L).enrich(
                devices = listOf(device("10.0.1.50")),
                networkContext = wifiContext(),
                generation = 1L,
                onResult = results::add,
            )
        }

        runCurrent()
        discovery.emit(
            MdnsDiscoveryEvent.ServiceResolved(
                observation(
                    serviceName = "Printer\u0000" + "x".repeat(200),
                    serviceType = "_ipp._tcp",
                    ipv4Addresses = listOf("10.0.1.50"),
                    generation = 1L,
                    txtAttributes = mapOf("model" to "Office\nPrinter"),
                    port = 70_000,
                ),
            ),
        )
        advanceUntilIdle()

        val sanitized = results.single().observation
        assertEquals(128, sanitized.serviceName.length)
        assertEquals("OfficePrinter", sanitized.txtAttributes["model"])
        assertEquals(null, sanitized.port)
        assertTrue(job.isCompleted)
    }

    private class FakeMdnsDiscovery(
        private val startFailure: Exception? = null,
        private val stopFailure: Exception? = null,
        private val startEvents: List<MdnsDiscoveryEvent> = emptyList(),
        private val stopEvents: List<MdnsDiscoveryEvent> = emptyList(),
    ) : MdnsDiscovery {
        lateinit var request: MdnsDiscoveryRequest
        lateinit var session: MdnsDiscoverySession
        private var callback: ((MdnsDiscoveryEvent) -> Unit)? = null
        private var sessionStopped = false
        var startCount: Int = 0
        var stopCount: Int = 0

        override fun start(
            request: MdnsDiscoveryRequest,
            onEvent: (MdnsDiscoveryEvent) -> Unit,
        ): MdnsDiscoverySession {
            startCount += 1
            startFailure?.let { throw it }
            this.request = request
            callback = onEvent
            startEvents.forEach(onEvent)
            session = MdnsDiscoverySession {
                if (!sessionStopped) {
                    sessionStopped = true
                    stopCount += 1
                    stopEvents.forEach { event -> callback?.invoke(event) }
                    stopFailure?.let { throw it }
                }
            }
            return session
        }

        fun emit(event: MdnsDiscoveryEvent) {
            callback?.invoke(event)
        }
    }

    private fun observation(
        serviceName: String,
        serviceType: String,
        ipv4Addresses: List<String>,
        generation: Long,
        ipv6Addresses: List<String> = emptyList(),
        txtAttributes: Map<String, String> = emptyMap(),
        port: Int? = null,
    ) = MdnsObservation(
        serviceName = serviceName,
        serviceType = serviceType,
        ipv4Addresses = ipv4Addresses,
        ipv6Addresses = ipv6Addresses,
        txtAttributes = txtAttributes,
        port = port,
        observedAt = 1L,
        generation = generation,
        networkIdentity = wifiContext().mdnsIdentityForTest(),
    )

    private fun device(ipAddress: String) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = false,
        isGateway = false,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(
            com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence(
                method = LanDiscoveryMethod.REACHABILITY,
            ),
        ),
        lastSeen = 1L,
    )

    private fun wifiContext(
        connectionType: ConnectionType = ConnectionType.WIFI,
    ) = NetworkContext(
        connectionType = connectionType,
        ipv4Address = "10.0.1.20",
        ipv6Address = "fe80::20",
        gateway = "10.0.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        interfaceName = "wlan0",
    )

    private fun NetworkContext.mdnsIdentityForTest() = listOf(
        connectionType.name,
        interfaceName.orEmpty(),
        ipv4Address.orEmpty(),
        ipv4PrefixLength?.toString().orEmpty(),
        gateway.orEmpty(),
    ).joinToString("|")
}
