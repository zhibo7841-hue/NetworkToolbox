package com.networktoolbox.feature.lanscan.presentation

import com.networktoolbox.core.network.model.ConnectionType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.feature.lanscan.domain.LanScanRangeCalculator
import com.networktoolbox.feature.lanscan.domain.LanScanReadiness
import com.networktoolbox.feature.lanscan.domain.LanScanRangeResult
import com.networktoolbox.feature.lanscan.domain.ObserveLanScanReadiness
import com.networktoolbox.feature.lanscan.domain.RunLanScan
import com.networktoolbox.feature.lanscan.domain.model.LanDevice
import com.networktoolbox.feature.lanscan.domain.model.LanDeviceEvidence
import com.networktoolbox.feature.lanscan.domain.model.LanDiscoveryMethod
import com.networktoolbox.feature.lanscan.domain.model.LanScanProbeConfig
import com.networktoolbox.feature.lanscan.domain.model.LanScanSession
import com.networktoolbox.feature.lanscan.domain.model.LanScanStatus
import com.networktoolbox.feature.lanscan.domain.model.LanScanUpdate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanScannerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ready state shows calculated range before user starts scan`() = runTest {
        val context = context("192.168.1.2", 30)
        val viewModel = viewModel(readiness(context))

        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Ready
        assertEquals("192.168.1.0/30", state.range.cidr)
        assertEquals(2, state.range.hostCount)
    }

    @Test
    fun `start exposes progressive devices and ends completed`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val device = device("192.168.1.3")
        val progressStates = mutableListOf<LanScannerUiState>()
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                onUpdate(update(range, scannedHosts = 1, devices = listOf(device)))
                yield()
                onUpdate(update(range, scannedHosts = 2, devices = listOf(device)))
                yield()
                return session(context, range, listOf(device))
            }
        }
        val viewModel = viewModel(readiness(context), runner)
        val collector = backgroundScope.launch {
            viewModel.uiState.collect { progressStates += it }
        }

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        collector.cancel()

        assertTrue(progressStates.any { state ->
            state is LanScannerUiState.Scanning &&
                state.update.scannedHosts == 1 &&
                state.update.discoveredDevices.single().ipAddress == "192.168.1.3"
        })
        val state = viewModel.uiState.value as LanScannerUiState.Completed
        assertEquals(listOf("192.168.1.3"), state.session.discoveredDevices.map { it.ipAddress })
    }

    @Test
    fun `stop cancels scan and keeps partial devices without error`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val device = device("192.168.1.3")
        val cancellationObserved = AtomicBoolean(false)
        val runner = object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession {
                onUpdate(update(range, scannedHosts = 1, devices = listOf(device)))
                try {
                    awaitCancellation()
                } finally {
                    cancellationObserved.set(true)
                }
            }
        }
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.startScan()
        runCurrent()
        viewModel.stopScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value as LanScannerUiState.Cancelled
        assertTrue(cancellationObserved.get())
        assertEquals(1, state.session.scannedHosts)
        assertEquals("192.168.1.3", state.session.discoveredDevices.single().ipAddress)
    }

    @Test
    fun `network changed result is shown separately from failure`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val runner = fakeRunner(session(context, range, emptyList(), LanScanStatus.NETWORK_CHANGED))
        val viewModel = viewModel(readiness(context), runner)

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is LanScannerUiState.NetworkChanged)
        assertFalse(viewModel.uiState.value is LanScannerUiState.Error)
    }

    @Test
    fun `network changed terminal state is not replaced by readiness refresh`() = runTest {
        val context = context("192.168.1.2", 30)
        val range = readyRange(context)
        val readinessFlow = MutableStateFlow(readiness(context))
        val viewModel = LanScannerViewModel(
            observeReadiness = ObserveLanScanReadiness { readinessFlow },
            runScan = fakeRunner(session(context, range, emptyList(), LanScanStatus.NETWORK_CHANGED)),
        )

        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()
        readinessFlow.value = readiness(context.copy(ipv4Address = "192.168.1.3"))
        runCurrent()

        assertTrue(viewModel.uiState.value is LanScannerUiState.NetworkChanged)
    }

    @Test
    fun `cellular and vpn readiness do not expose a startable state`() = runTest {
        val cellularContext = context("10.0.0.5", 24).copy(connectionType = ConnectionType.CELLULAR)
        val vpnContext = context("192.168.1.5", 24).copy(vpnActive = true)
        val cellularViewModel = viewModel(readiness(cellularContext))
        val vpnViewModel = viewModel(readiness(vpnContext))

        advanceUntilIdle()

        assertTrue(cellularViewModel.uiState.value is LanScannerUiState.UnsupportedNetwork)
        assertTrue(vpnViewModel.uiState.value is LanScannerUiState.VpnBlocked)
    }

    @Test
    fun `presentation labels local gateway and tcp evidence`() {
        val local = device("192.168.1.5", isLocal = true)
        val gateway = device("192.168.1.1", isGateway = true)
        val tcp = device(
            ipAddress = "192.168.1.20",
            evidence = listOf(
                LanDeviceEvidence(
                    method = LanDiscoveryMethod.TCP,
                    successfulPort = 445,
                ),
            ),
        )

        assertEquals("本机", LanScannerPresentation.deviceRole(local))
        assertEquals("网关", LanScannerPresentation.deviceRole(gateway))
        assertEquals("TCP 445 可连接", LanScannerPresentation.discoveryEvidence(tcp))
        assertEquals(0.5f, LanScannerPresentation.progressFraction(1, 2))
    }

    private fun viewModel(
        readiness: LanScanReadiness,
        runner: RunLanScan? = null,
        readinessFlow: Flow<LanScanReadiness> = flowOf(readiness),
    ): LanScannerViewModel {
        val effectiveRunner = runner ?: object : RunLanScan {
            override suspend fun invoke(
                probeConfig: LanScanProbeConfig,
                onUpdate: (LanScanUpdate) -> Unit,
            ): LanScanSession = error("scan should not be started in this test")
        }
        return LanScannerViewModel(
            observeReadiness = ObserveLanScanReadiness { readinessFlow },
            runScan = effectiveRunner,
        )
    }

    private fun readiness(context: NetworkContext): LanScanReadiness = LanScanReadiness(
        networkContext = context,
        rangeResult = LanScanRangeCalculator().calculate(context),
    )

    private fun readyRange(context: NetworkContext) =
        (LanScanRangeCalculator().calculate(context) as LanScanRangeResult.Ready).range

    private fun fakeRunner(
        session: LanScanSession,
    ) = object : RunLanScan {
        override suspend fun invoke(
            probeConfig: LanScanProbeConfig,
            onUpdate: (LanScanUpdate) -> Unit,
        ): LanScanSession = session
    }

    private fun update(
        range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
        scannedHosts: Int,
        devices: List<LanDevice>,
    ) = LanScanUpdate(
        status = LanScanStatus.SCANNING,
        scannedHosts = scannedHosts,
        totalHosts = range.hostCount,
        discoveredDevices = devices,
    )

    private fun session(
        context: NetworkContext,
        range: com.networktoolbox.feature.lanscan.domain.model.LanScanRange,
        devices: List<LanDevice>,
        status: LanScanStatus = LanScanStatus.COMPLETED,
    ) = LanScanSession(
        status = status,
        initialNetworkContext = context,
        range = range,
        scannedHosts = if (status == LanScanStatus.COMPLETED) range.hostCount else 0,
        totalHosts = range.hostCount,
        discoveredDevices = devices,
        startedAt = 0,
        finishedAt = 1,
    )

    private fun device(
        ipAddress: String,
        isLocal: Boolean = false,
        isGateway: Boolean = false,
        evidence: List<LanDeviceEvidence> = listOf(
            LanDeviceEvidence(LanDiscoveryMethod.REACHABILITY),
        ),
    ) = LanDevice(
        ipAddress = ipAddress,
        isLocalDevice = isLocal,
        isGateway = isGateway,
        discoveryMethods = evidence.map(LanDeviceEvidence::method),
        discoveryEvidence = evidence,
        lastSeen = 1,
    )

    private fun context(address: String, prefixLength: Int) = NetworkContext(
        connectionType = ConnectionType.WIFI,
        ipv4Address = address,
        ipv6Address = null,
        gateway = "192.168.1.1",
        dnsServers = emptyList(),
        vpnActive = false,
        wifiName = null,
        wifiSignalLevel = null,
        activeNetworkAvailable = true,
        validated = true,
        ipv4PrefixLength = prefixLength,
    )
}
