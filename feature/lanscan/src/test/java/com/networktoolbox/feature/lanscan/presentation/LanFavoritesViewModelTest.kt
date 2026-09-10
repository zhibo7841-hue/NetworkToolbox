package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.ReverseDnsEnricher
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanFavoritesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observed device can be favorited and resolved through detail route`() = runTest {
        val context = context()
        val device = device("10.0.1.20")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        val favorite = viewModel.favoriteDevices.value.single()
        val route = viewModel.detailRouteKey(device)
        val detail = viewModel.resolveDeviceDetail(route)

        assertEquals("10.0.1.20", favorite.lastKnownIpv4)
        assertTrue(favorite.isLocalDevice.not())
        assertNotNull(detail)
        assertTrue(detail!!.isFavorite)
        assertTrue(detail.observedThisScan)
    }

    @Test
    fun `toggling the same observed device removes its favorite`() = runTest {
        val context = context()
        val device = device("10.0.1.20")
        val repository = FakeFavoriteDeviceRepository()
        val viewModel = viewModel(context, device, repository)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()
        viewModel.toggleFavorite(device)
        advanceUntilIdle()

        assertTrue(viewModel.favoriteDevices.value.isEmpty())
    }

    private fun viewModel(
        context: NetworkContext,
        device: LanDevice,
        repository: FavoriteDeviceRepository,
    ) = LanScannerViewModel(
        observeReadiness = ObserveLanScanReadiness {
            flowOf(
                LanScanReadiness(
                    networkContext = context,
                    rangeResult = LanScanRangeCalculator().calculate(context),
                ),
            )
        },
        runScan = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                val range = (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range
                return LanScanSession(
                    status = LanScanStatus.COMPLETED,
                    initialNetworkContext = context,
                    range = range,
                    scannedHosts = range.hostCount,
                    totalHosts = range.hostCount,
                    discoveredDevices = listOf(device),
                    startedAt = 0L,
                    finishedAt = 1L,
                )
            }
        },
        reverseDnsEnricher = ReverseDnsEnricher { _, _ -> },
        mdnsEnricher = com.networktoolbox.feature.lanscan.domain.MdnsEnricher { _, _, _, _ -> },
        favoriteRepository = repository,
    )

    private fun context() = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = "10.0.1.206",
        ipv6Address = null,
        gateway = "10.0.1.1",
        dnsServers = listOf("10.0.1.1"),
        vpnActive = false,
        wifiName = "HomeLab",
        wifiSignalLevel = 4,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = 24,
        interfaceName = "wlan0",
    )

    private fun device(ip: String) = LanDevice(
        ipAddress = ip,
        isLocalDevice = false,
        isGateway = false,
        latencyMs = 14L,
        discoveryMethods = listOf(LanDiscoveryMethod.REACHABILITY),
        discoveryEvidence = listOf(LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY)),
        lastSeen = 42L,
    )
}

private class FakeFavoriteDeviceRepository : FavoriteDeviceRepository {
    private val state = MutableStateFlow<List<FavoriteDevice>>(emptyList())
    private var nextId = 1L

    override fun observeFavorites(): Flow<List<FavoriteDevice>> = state

    override suspend fun add(favorite: FavoriteDevice): Long {
        val id = nextId++
        state.value = state.value + favorite.copy(id = id)
        return id
    }

    override suspend fun remove(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) {
        state.value = state.value.map { favorite ->
            if (favorite.id != id) favorite else favorite.copy(
                lastKnownIpv4 = observation.lastKnownIpv4,
                lastKnownDisplayName = observation.lastKnownDisplayName,
                lastKnownHostname = observation.lastKnownHostname,
                lastKnownMdnsName = observation.lastKnownMdnsName,
                lastKnownUpnpName = observation.lastKnownUpnpName,
                macAddress = observation.macAddress,
                vendor = observation.vendor,
                model = observation.model,
                lastSeenAt = observation.lastSeenAt,
                isGateway = observation.isGateway,
                isLocalDevice = observation.isLocalDevice,
            )
        }
    }

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice? =
        state.value.firstOrNull { favorite ->
            com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher.matches(favorite, candidate)
        }
}
