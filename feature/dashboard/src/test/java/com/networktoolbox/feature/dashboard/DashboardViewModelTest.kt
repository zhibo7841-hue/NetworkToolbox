package com.networktoolbox.feature.dashboard

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.core.network.model.NetworkContext
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import com.networktoolbox.feature.dashboard.domain.ObserveRecentDiagnosisUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
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
    fun recentDiagnosisUpdatesWithoutScreenRecreation() = runTest {
        val source = MutableStateFlow<List<HistoryRecord>>(emptyList())
        val viewModel = viewModelFor(source)
        val observed = mutableListOf<HistoryRecord?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.recentDiagnosis.collect { observed += it }
        }

        advanceUntilIdle()
        assertNull(viewModel.recentDiagnosis.value)

        val first = reportRecord(id = 1L, timestamp = 1_000L)
        source.value = listOf(first)
        advanceUntilIdle()
        assertEquals(first, viewModel.recentDiagnosis.value)

        val second = reportRecord(id = 2L, timestamp = 2_000L)
        source.value = listOf(second, first)
        advanceUntilIdle()
        assertEquals(second, viewModel.recentDiagnosis.value)

        source.value = listOf(first)
        advanceUntilIdle()
        assertEquals(first, viewModel.recentDiagnosis.value)

        source.value = emptyList()
        advanceUntilIdle()
        assertNull(viewModel.recentDiagnosis.value)
        job.cancel()
    }

    @Test
    fun existingLatestIsAvailableToARecreatedViewModel() = runTest {
        val existing = reportRecord(id = 3L, timestamp = 3_000L)
        val source = MutableStateFlow(listOf(existing))

        val recreated = viewModelFor(source)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recreated.recentDiagnosis.collect {}
        }
        advanceUntilIdle()

        assertEquals(existing, recreated.recentDiagnosis.value)
        job.cancel()
    }

    private fun viewModelFor(source: MutableStateFlow<List<HistoryRecord>>) = DashboardViewModel(
        observeNetworkContext = ObserveNetworkContextUseCase(
            object : NetworkRepository {
                override fun observeNetworkContext(): Flow<NetworkContext> =
                    flowOf(NetworkContext.unknown())
            },
        ),
        observeRecentDiagnosis = ObserveRecentDiagnosisUseCase(
            object : HistoryRepository {
                override suspend fun save(record: HistoryRecord) = Unit
                override suspend fun getHistory(): List<HistoryRecord> = source.value
                override fun observeHistory(): Flow<List<HistoryRecord>> = source
                override suspend fun delete(id: Long) = Unit
                override suspend fun clear() = Unit
            },
        ),
    )

    private fun reportRecord(id: Long, timestamp: Long) = HistoryRecord(
        id = id,
        timestamp = timestamp,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = "网络状态正常",
        detailJson = "{\"schemaVersion\":3}",
    )
}
