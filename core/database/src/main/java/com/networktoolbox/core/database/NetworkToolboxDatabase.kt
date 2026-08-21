package com.networktoolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NetworkToolboxDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
