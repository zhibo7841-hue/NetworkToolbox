package com.networktoolbox.core.database

import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
) : HistoryRepository {
    override suspend fun save(record: HistoryRecord) {
        historyDao.insert(record.toEntity())
    }

    override suspend fun getHistory(): List<HistoryRecord> =
        historyDao.getAll().map(HistoryEntity::toRecord)

    override fun observeHistory(): Flow<List<HistoryRecord>> =
        historyDao.observeAll().map { records -> records.map(HistoryEntity::toRecord) }

    override suspend fun delete(id: Long) {
        historyDao.deleteById(id)
    }

    override suspend fun clear() {
        historyDao.clear()
    }
}
