package com.networktoolbox.feature.ping.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingMode
import com.networktoolbox.core.network.ping.PingProtocol
import com.networktoolbox.core.network.ping.PingQualityLevel
import com.networktoolbox.core.network.ping.PingSessionResult
import com.networktoolbox.feature.ping.FakePingSessionEngine
import com.networktoolbox.feature.ping.domain.ExecutePingSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PingViewModelTest {
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
    fun initialStateIsIdle() {
        val viewModel = viewModelFor(successfulResult())

        assertEquals("", viewModel.uiState.value.targetInput)
        assertEquals(PingDetectionMode.QUICK, viewModel.uiState.value.mode)
        assertEquals(PingStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun successfulSessionMovesFromRunningToSuccessAndSavesOneRecord() = runTest {
        val savedRecords = mutableListOf<HistoryRecord>()
        val engine = FakePingSessionEngine(successfulResult(sentPackets = 3))
        val viewModel = viewModelFor(engine, savedRecords)
        viewModel.onTargetChanged("127.0.0.1")

        viewModel.ping()

        assertTrue(viewModel.uiState.value.status is PingStatus.Running)
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is PingStatus.Success)
        assertEquals(3, (status as PingStatus.Success).result.sentPackets)
        assertEquals(1, savedRecords.size)
        assertEquals(HistoryType.PING, savedRecords.single().type)
    }

    @Test
    fun failedSessionMovesToFailedWithUserVisibleResult() = runTest {
        val viewModel = viewModelFor(failedResult())
        viewModel.onTargetChanged("not..a.target")

        viewModel.ping()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is PingStatus.Failed)
        assertEquals("Invalid target.", (status as PingStatus.Failed).result.errorMessage)
    }

    @Test
    fun continuousModePassesCountAndIntervalToSessionEngine() = runTest {
        val engine = FakePingSessionEngine(successfulResult(sentPackets = 3))
        val viewModel = viewModelFor(engine)
        viewModel.onTargetChanged("example.com")
        viewModel.onModeChanged(PingDetectionMode.CONTINUOUS)
        viewModel.onCountChanged("3")
        viewModel.onIntervalChanged("500")

        viewModel.startPing()
        advanceUntilIdle()

        assertEquals(PingMode.CONTINUOUS, engine.receivedRequest?.mode)
        assertEquals(3, engine.receivedRequest?.count)
        assertEquals(500, engine.receivedRequest?.intervalMs)
    }

    @Test
    fun stoppingContinuousSessionShowsCancelledAndDoesNotSaveHistory() = runTest {
        val savedRecords = mutableListOf<HistoryRecord>()
        val engine = FakePingSessionEngine(waitForCancellation = true)
        val viewModel = viewModelFor(engine, savedRecords)
        viewModel.onTargetChanged("example.com")
        viewModel.onModeChanged(PingDetectionMode.CONTINUOUS)

        viewModel.startPing()
        runCurrent()
        assertTrue(viewModel.uiState.value.status is PingStatus.Running)

        viewModel.stopPing()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is PingStatus.Cancelled)
        assertEquals(0, savedRecords.size)
    }

    private fun viewModelFor(
        result: PingSessionResult,
        savedRecords: MutableList<HistoryRecord> = mutableListOf(),
    ): PingViewModel = viewModelFor(
        engine = FakePingSessionEngine(result),
        savedRecords = savedRecords,
    )

    private fun viewModelFor(
        engine: FakePingSessionEngine,
        savedRecords: MutableList<HistoryRecord> = mutableListOf(),
    ): PingViewModel = PingViewModel(
        executePingSession = ExecutePingSessionUseCase(
            pingSessionEngine = engine,
            historyRecorder = HistoryRecorder { savedRecords += it },
        ),
    )

    private fun successfulResult(sentPackets: Int = 1): PingSessionResult =
        PingSessionResult(
            target = "127.0.0.1",
            address = "127.0.0.1",
            protocol = PingProtocol.IPV4,
            mode = if (sentPackets == 1) PingMode.SINGLE else PingMode.CONTINUOUS,
            startTime = 1_000L,
            endTime = 2_000L,
            sentPackets = sentPackets,
            receivedPackets = sentPackets,
            lostPackets = 0,
            packetLoss = 0.0,
            minLatencyMs = 1L,
            avgLatencyMs = 2.0,
            maxLatencyMs = 3L,
            jitterMs = 1.0,
            qualityLevel = PingQualityLevel.EXCELLENT,
            summary = "Excellent observed network quality.",
            method = PingMethod.SYSTEM_REACHABILITY,
            errorMessage = null,
        )

    private fun failedResult(): PingSessionResult = successfulResult().copy(
        receivedPackets = 0,
        lostPackets = 1,
        packetLoss = 100.0,
        qualityLevel = PingQualityLevel.UNKNOWN,
        summary = "无法完成 Ping 检测。",
        method = PingMethod.UNAVAILABLE,
        errorMessage = "Invalid target.",
    )
}
