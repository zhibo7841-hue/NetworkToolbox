package com.networktoolbox.feature.ping.presentation

import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.ping.PingMethod
import com.networktoolbox.core.network.ping.PingResult
import com.networktoolbox.feature.ping.FakePingEngine
import com.networktoolbox.feature.ping.domain.ExecutePingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        val viewModel = viewModelFor(
            PingResult(
                target = "127.0.0.1",
                success = true,
                latencyMs = 1L,
                method = PingMethod.SYSTEM_REACHABILITY,
                errorMessage = null,
            ),
        )

        assertEquals("", viewModel.uiState.value.targetInput)
        assertEquals(PingStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun successfulResultMovesFromRunningToSuccess() = runTest {
        val viewModel = viewModelFor(
            PingResult(
                target = "127.0.0.1",
                success = true,
                latencyMs = 2L,
                method = PingMethod.SYSTEM_REACHABILITY,
                errorMessage = null,
            ),
        )
        viewModel.onTargetChanged("127.0.0.1")

        viewModel.ping()

        assertTrue(viewModel.uiState.value.status is PingStatus.Running)
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is PingStatus.Success)
        assertEquals(2L, (status as PingStatus.Success).result.latencyMs)
    }

    @Test
    fun failedResultMovesFromRunningToFailed() = runTest {
        val viewModel = viewModelFor(
            PingResult(
                target = "127.0.0.1",
                success = false,
                latencyMs = null,
                method = PingMethod.SYSTEM_REACHABILITY,
                errorMessage = "Target is not reachable.",
            ),
        )
        viewModel.onTargetChanged("127.0.0.1")

        viewModel.ping()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertTrue(status is PingStatus.Failed)
        assertEquals(
            "Target is not reachable.",
            (status as PingStatus.Failed).result.errorMessage,
        )
    }

    private fun viewModelFor(result: PingResult): PingViewModel =
        PingViewModel(
            ExecutePingUseCase(
                pingEngine = FakePingEngine(result),
                historyRecorder = HistoryRecorder { },
            ),
        )
}
