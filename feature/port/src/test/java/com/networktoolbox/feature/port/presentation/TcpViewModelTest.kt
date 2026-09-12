package com.networktoolbox.feature.port.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRecorder
import com.networktoolbox.core.network.tcp.TcpProbeResult
import com.networktoolbox.feature.port.FakeTcpPortChecker
import com.networktoolbox.feature.port.domain.CheckTcpPortUseCase
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
class TcpViewModelTest {
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
        val viewModel = viewModelFor(successResult())

        assertEquals("", viewModel.uiState.value.hostInput)
        assertEquals("", viewModel.uiState.value.portInput)
        assertEquals(TcpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun deviceDetailNavigationHostIsPrefilledWithoutStartingCheck() {
        val savedRecords = mutableListOf<HistoryRecord>()
        val viewModel = viewModelFor(successResult(), savedRecords)

        viewModel.applyNavigationHost("10.0.1.10")

        assertEquals("10.0.1.10", viewModel.uiState.value.hostInput)
        assertEquals("", viewModel.uiState.value.portInput)
        assertEquals(TcpStatus.Idle, viewModel.uiState.value.status)
        assertTrue(savedRecords.isEmpty())
    }

    @Test
    fun standardTcpEntryClearsTransientDeviceHost() {
        val viewModel = viewModelFor(successResult())
        viewModel.applyNavigationHost("10.0.1.10")

        viewModel.applyNavigationHost(null)

        assertEquals("", viewModel.uiState.value.hostInput)
        assertEquals(TcpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun checkEntersLoadingBeforeCompleting() = runTest {
        val viewModel = viewModelFor(successResult())
        viewModel.onHostChanged("192.0.2.10")
        viewModel.onPortChanged("443")

        viewModel.check()

        assertEquals(
            TcpStatus.Loading("192.0.2.10", "443"),
            viewModel.uiState.value.status,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.status is TcpStatus.Success)
    }

    @Test
    fun successfulResultProducesSuccessState() = runTest {
        val expected = successResult()
        val viewModel = viewModelFor(expected)
        viewModel.onHostChanged("192.0.2.10")
        viewModel.onPortChanged("443")

        viewModel.check()
        advanceUntilIdle()

        assertEquals(TcpStatus.Success(expected), viewModel.uiState.value.status)
    }

    @Test
    fun failedResultProducesErrorState() = runTest {
        val expected = TcpProbeResult(
            host = "192.0.2.10",
            port = 443,
            success = false,
            latencyMs = null,
            errorMessage = "Connection refused",
        )
        val viewModel = viewModelFor(expected)
        viewModel.onHostChanged("192.0.2.10")
        viewModel.onPortChanged("443")

        viewModel.check()
        advanceUntilIdle()

        assertEquals(TcpStatus.Error(expected), viewModel.uiState.value.status)
    }

    private fun viewModelFor(
        result: TcpProbeResult,
        savedRecords: MutableList<HistoryRecord> = mutableListOf(),
    ): TcpViewModel =
        TcpViewModel(
            CheckTcpPortUseCase(
                tcpPortChecker = FakeTcpPortChecker(result),
                historyRecorder = HistoryRecorder { savedRecords += it },
            ),
        )

    private fun successResult(): TcpProbeResult = TcpProbeResult(
        host = "192.0.2.10",
        port = 443,
        success = true,
        latencyMs = 8,
        errorMessage = null,
    )
}
