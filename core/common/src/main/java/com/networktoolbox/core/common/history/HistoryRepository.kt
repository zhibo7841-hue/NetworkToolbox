package com.networktoolbox.core.common.history

interface HistoryRepository {
    suspend fun save(record: HistoryRecord)

    suspend fun getHistory(): List<HistoryRecord>

    suspend fun delete(id: Long)

    suspend fun clear()
}
