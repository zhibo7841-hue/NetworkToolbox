package com.networktoolbox.feature.dashboard.domain

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRecentDiagnosisUseCaseTest {
    @Test
    fun emptyHistoryEmitsNull() = runTest {
        val source = MutableStateFlow<List<HistoryRecord>>(emptyList())

        assertNull(ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))().first())
    }

    @Test
    fun insertUpdatesLatestWithoutRecreatingObserver() = runTest {
        val source = MutableStateFlow<List<HistoryRecord>>(emptyList())
        val observed = mutableListOf<HistoryRecord?>()
        val observer = ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.collect { observed += it }
        }

        advanceUntilIdle()
        val record = reportRecord(id = 1L, timestamp = 2_000L)
        source.value = listOf(record)
        advanceUntilIdle()

        assertEquals(record, observed.last())
        job.cancel()
    }

    @Test
    fun newerRecordReplacesExistingLatest() = runTest {
        val source = MutableStateFlow(
            listOf(reportRecord(id = 1L, timestamp = 1_000L)),
        )
        val useCase = ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))

        val previous = reportRecord(id = 1L, timestamp = 1_000L)
        val newer = reportRecord(id = 2L, timestamp = 2_000L)
        source.value = listOf(newer, previous)

        assertEquals(newer, useCase().first())
    }

    @Test
    fun deletingLatestFallsBackToPreviousRecord() = runTest {
        val previous = reportRecord(id = 1L, timestamp = 1_000L)
        val latest = reportRecord(id = 2L, timestamp = 2_000L)
        val source = MutableStateFlow(listOf(latest, previous))
        val useCase = ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))

        source.value = listOf(previous)

        assertEquals(previous, useCase().first())
    }

    @Test
    fun clearHistoryEmitsEmptyLatest() = runTest {
        val source = MutableStateFlow(
            listOf(reportRecord(id = 1L, timestamp = 1_000L)),
        )
        val useCase = ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))

        source.value = emptyList()

        assertNull(useCase().first())
    }

    @Test
    fun latestUsesTimestampAndIdInsteadOfListOrder() = runTest {
        val older = reportRecord(id = 99L, timestamp = 1_000L)
        val newer = reportRecord(id = 1L, timestamp = 2_000L)
        val source = MutableStateFlow(listOf(older, newer))

        assertEquals(
            newer,
            ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))().first(),
        )
    }

    @Test
    fun nonReportHistoryDoesNotBecomeHomeRecentDiagnosis() = runTest {
        val source = MutableStateFlow(
            listOf(
                reportRecord(id = 1L, timestamp = 1_000L),
                HistoryRecord(
                    id = 2L,
                    timestamp = 3_000L,
                    type = HistoryType.PING,
                    title = "Ping",
                    summary = "完成",
                    detailJson = "{}",
                ),
            ),
        )

        assertEquals(
            reportRecord(id = 1L, timestamp = 1_000L),
            ObserveRecentDiagnosisUseCase(FakeHistoryRepository(source))().first(),
        )
    }

    private fun reportRecord(id: Long, timestamp: Long) = HistoryRecord(
        id = id,
        timestamp = timestamp,
        type = HistoryType.REPORT,
        title = "网络诊断",
        summary = "网络状态正常",
        detailJson = "{\"schemaVersion\":3}",
    )
}

private class FakeHistoryRepository(
    private val source: Flow<List<HistoryRecord>>,
) : HistoryRepository {
    override suspend fun save(record: HistoryRecord) = Unit

    override suspend fun getHistory(): List<HistoryRecord> = emptyList()

    override fun observeHistory(): Flow<List<HistoryRecord>> = source

    override suspend fun delete(id: Long) = Unit

    override suspend fun clear() = Unit
}
