package com.networktoolbox.feature.history.presentation

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryType
import com.networktoolbox.feature.history.domain.ClearHistoryUseCase
import com.networktoolbox.feature.history.domain.DeleteHistoryUseCase
import com.networktoolbox.feature.history.domain.GetHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun initialStateIsLoading() {
        val viewModel = viewModelFor(FakeHistoryRepository())

        assertEquals(HistoryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun loadsHistoryIntoSuccessState() = runTest {
        val record = historyRecord(id = 1L)
        val repository = FakeHistoryRepository(records = listOf(record))
        val viewModel = viewModelFor(repository)

        advanceUntilIdle()

        assertEquals(HistoryUiState.Success(listOf(record)), viewModel.uiState.value)
    }

    @Test
    fun emptyHistoryProducesEmptyState() = runTest {
        val viewModel = viewModelFor(FakeHistoryRepository())

        advanceUntilIdle()

        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun deleteCallsRepositoryAndRefreshesList() = runTest {
        val first = historyRecord(id = 1L)
        val second = historyRecord(id = 2L)
        val repository = FakeHistoryRepository(records = listOf(first, second))
        val viewModel = viewModelFor(repository)

        advanceUntilIdle()
        viewModel.delete(first.id)
        advanceUntilIdle()

        assertEquals(listOf(first.id), repository.deletedIds)
        assertEquals(HistoryUiState.Success(listOf(second)), viewModel.uiState.value)
    }

    @Test
    fun clearCallsRepositoryAndShowsEmptyState() = runTest {
        val repository = FakeHistoryRepository(
            records = listOf(historyRecord(id = 1L), historyRecord(id = 2L)),
        )
        val viewModel = viewModelFor(repository)

        advanceUntilIdle()
        viewModel.clear()
        advanceUntilIdle()

        assertEquals(1, repository.clearCallCount)
        assertEquals(HistoryUiState.Empty, viewModel.uiState.value)
    }

    private fun viewModelFor(repository: FakeHistoryRepository): HistoryViewModel =
        HistoryViewModel(
            getHistory = GetHistoryUseCase(repository),
            deleteHistory = DeleteHistoryUseCase(repository),
            clearHistory = ClearHistoryUseCase(repository),
        )
}

private class FakeHistoryRepository(
    records: List<HistoryRecord> = emptyList(),
) : HistoryRepository {
    private val records = records.toMutableList()
    val deletedIds = mutableListOf<Long>()
    var clearCallCount: Int = 0
        private set

    override suspend fun save(record: HistoryRecord) {
        records += record
    }

    override suspend fun getHistory(): List<HistoryRecord> = records.toList()

    override suspend fun delete(id: Long) {
        deletedIds += id
        records.removeAll { it.id == id }
    }

    override suspend fun clear() {
        clearCallCount += 1
        records.clear()
    }
}

private fun historyRecord(id: Long): HistoryRecord = HistoryRecord(
    id = id,
    timestamp = 1_700_000_000_000L + id,
    type = HistoryType.PING,
    title = "Ping · example.com",
    summary = "Ping completed",
    detailJson = "{\"target\":\"example.com\"}",
)
