package com.networktoolbox.core.common.favorites

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface FavoriteDeviceRepository {
    fun observeFavorites(): Flow<List<FavoriteDevice>>

    suspend fun add(favorite: FavoriteDevice): Long

    suspend fun remove(id: Long)

    suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation)

    suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice?
}

/** Keeps pure feature tests and non-database callers explicit and harmless. */
object NoOpFavoriteDeviceRepository : FavoriteDeviceRepository {
    override fun observeFavorites(): Flow<List<FavoriteDevice>> = emptyFlow()

    override suspend fun add(favorite: FavoriteDevice): Long = 0L

    override suspend fun remove(id: Long) = Unit

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) = Unit

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice? = null
}
