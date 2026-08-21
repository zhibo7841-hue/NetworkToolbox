package com.networktoolbox.core.database

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomHistoryRepositoryTest {
    @Test
    fun saveAndQueryReturnStoredHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        val record = historyRecord(type = HistoryType.PING)

        repository.save(record)

        val history = repository.getHistory()
        assertEquals(1, history.size)
        assertEquals(HistoryType.PING, history.single().type)
        assertEquals(record.title, history.single().title)
        assertEquals(record.detailJson, history.single().detailJson)
    }

    @Test
    fun queryMapsNewestHistoryFirst() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(timestamp = 100L, title = "Older"))
        repository.save(historyRecord(timestamp = 200L, title = "Newer"))

        val history = repository.getHistory()

        assertEquals(listOf("Newer", "Older"), history.map { it.title })
    }

    @Test
    fun deleteRemovesOnlyRequestedHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(title = "First"))
        repository.save(historyRecord(title = "Second"))
        val saved = repository.getHistory()

        repository.delete(saved.first { it.title == "First" }.id)

        assertEquals(listOf("Second"), repository.getHistory().map { it.title })
    }

    @Test
    fun clearRemovesAllHistory() = runBlocking {
        val repository = RoomHistoryRepository(FakeHistoryDao())
        repository.save(historyRecord(title = "First"))
        repository.save(historyRecord(title = "Second"))

        repository.clear()

        assertTrue(repository.getHistory().isEmpty())
    }

    private fun historyRecord(
        timestamp: Long = 100L,
        type: HistoryType = HistoryType.REPORT,
        title: String = "History",
    ) = HistoryRecord(
        timestamp = timestamp,
        type = type,
        title = title,
        summary = "Summary",
        detailJson = "{\"value\":true}",
    )
}

private class FakeHistoryDao : HistoryDao {
    private val records = mutableListOf<HistoryEntity>()
    private var nextId = 1L

    override suspend fun insert(record: HistoryEntity): Long {
        val stored = record.copy(id = record.id.takeIf { it > 0 } ?: nextId++)
        records.removeAll { it.id == stored.id }
        records += stored
        return stored.id
    }

    override suspend fun getAll(): List<HistoryEntity> = records
        .sortedWith(compareByDescending<HistoryEntity> { it.timestamp }.thenByDescending { it.id })

    override suspend fun deleteById(id: Long) {
        records.removeAll { it.id == id }
    }

    override suspend fun clear() {
        records.clear()
    }
}
