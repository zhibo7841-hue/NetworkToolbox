package com.networktoolbox.core.common.history

import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    suspend fun save(record: HistoryRecord)

    suspend fun getHistory(): List<HistoryRecord>

    fun observeHistory(): Flow<List<HistoryRecord>>

    suspend fun delete(id: Long)

    suspend fun clear()
}
