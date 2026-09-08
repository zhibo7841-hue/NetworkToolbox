package com.networktoolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HistoryEntity): Long

    @Query("SELECT * FROM history_records ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<HistoryEntity>

    @Query("SELECT * FROM history_records ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_records")
    suspend fun clear()
}
