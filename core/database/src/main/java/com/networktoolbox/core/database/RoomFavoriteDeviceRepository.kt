package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteDeviceRepository @Inject constructor(
    private val favoriteDeviceDao: FavoriteDeviceDao,
) : FavoriteDeviceRepository {
    override fun observeFavorites(): Flow<List<FavoriteDevice>> =
        favoriteDeviceDao.observeAll().map { entities ->
            entities.mapNotNull(FavoriteDeviceEntity::toFavoriteDevice)
        }

    override suspend fun add(favorite: FavoriteDevice): Long {
        val insertedId = favoriteDeviceDao.insert(favorite.toEntity())
        if (insertedId != -1L) return insertedId

        return favoriteDeviceDao.findByIdentity(
            identityType = favorite.identityType.name,
            identityValue = favorite.identityValue,
            networkScope = favorite.networkScope,
        )?.id ?: 0L
    }

    override suspend fun remove(id: Long) {
        favoriteDeviceDao.deleteById(id)
    }

    override suspend fun updateLastObserved(id: Long, observation: FavoriteDeviceObservation) {
        favoriteDeviceDao.updateObserved(
            id = id,
            lastKnownIpv4 = observation.lastKnownIpv4,
            lastKnownDisplayName = observation.lastKnownDisplayName,
            lastKnownHostname = observation.lastKnownHostname,
            lastKnownMdnsName = observation.lastKnownMdnsName,
            lastKnownUpnpName = observation.lastKnownUpnpName,
            macAddress = FavoriteIdentityMatcher.normalizeMac(observation.macAddress),
            vendor = observation.vendor,
            model = observation.model,
            lastSeenAt = observation.lastSeenAt,
            isGateway = if (observation.isGateway) 1 else 0,
            isLocalDevice = if (observation.isLocalDevice) 1 else 0,
        )
    }

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): FavoriteDevice? =
        favoriteDeviceDao.getAll()
            .mapNotNull(FavoriteDeviceEntity::toFavoriteDevice)
            .firstOrNull { favorite -> FavoriteIdentityMatcher.matches(favorite, candidate) }
}
