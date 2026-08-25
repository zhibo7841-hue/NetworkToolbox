package com.networktoolbox.core.database

import android.content.Context
import androidx.room.Room
import com.networktoolbox.core.common.history.HistoryRepository
import com.networktoolbox.core.common.history.HistoryRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NetworkToolboxDatabase = Room.databaseBuilder(
        context,
        NetworkToolboxDatabase::class.java,
        DATABASE_NAME,
    ).build()

    @Provides
    @Singleton
    fun provideHistoryDao(database: NetworkToolboxDatabase): HistoryDao = database.historyDao()

    @Provides
    @Singleton
    fun provideHistoryRepository(historyDao: HistoryDao): HistoryRepository =
        RoomHistoryRepository(historyDao)

    @Provides
    @Singleton
    fun provideHistoryRecorder(historyRepository: HistoryRepository): HistoryRecorder =
        RoomHistoryRecorder(historyRepository)

    private const val DATABASE_NAME = "networktoolbox.db"
}
