package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.DeviceDisplayNameResolver
import com.networktoolbox.core.common.favorites.FavoriteIdentityMatcher
import com.networktoolbox.core.common.favorites.SavedDeviceProfile
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
import javax.inject.Inject
import com.networktoolbox.core.common.wol.WakeOnLanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteDeviceRepository @Inject constructor(
    private val favoriteDeviceDao: FavoriteDeviceDao,
) : SavedDeviceRepository, FavoriteDeviceRepository {
    override fun observeProfiles(): Flow<List<SavedDeviceProfile>> =
        favoriteDeviceDao.observeAll().map { entities ->
            entities.mapNotNull(FavoriteDeviceEntity::toSavedDeviceProfile)
        }

    override suspend fun save(profile: SavedDeviceProfile): Long {
        val insertedId = favoriteDeviceDao.insert(profile.toEntity())
        if (insertedId != -1L) return insertedId

        return favoriteDeviceDao.findByIdentity(
            identityType = profile.identityType.name,
            identityValue = profile.identityValue,
            networkScope = profile.networkScope,
        )?.id ?: 0L
    }

    override suspend fun delete(id: Long) {
        favoriteDeviceDao.deleteById(id)
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        val existing = favoriteDeviceDao.findById(id) ?: return
        val now = System.currentTimeMillis()
        if (!isFavorite && existing.customName.isNullOrBlank() && existing.wolMacAddress == null) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateFavorite(
                id = id,
                isFavorite = if (isFavorite) 1 else 0,
                updatedAt = now,
            )
        }
    }

    override suspend fun setCustomName(id: Long, customName: String?) {
        val normalized = customName?.let {
            DeviceDisplayNameResolver.validateCustomName(it).getOrElse { error -> throw error }
        }
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (normalized == null && existing.isFavorite == 0 && existing.wolMacAddress == null) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateCustomName(
                id = id,
                customName = normalized,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setWakeOnLanConfig(id: Long, config: WakeOnLanConfig?) {
        val existing = favoriteDeviceDao.findById(id) ?: return
        if (config == null && existing.isFavorite == 0 && existing.customName.isNullOrBlank()) {
            favoriteDeviceDao.deleteById(id)
        } else {
            favoriteDeviceDao.updateWakeOnLan(
                id = id,
                wolMacAddress = config?.macAddress?.toString(),
                wolUdpPort = config?.udpPort,
                updatedAt = System.currentTimeMillis(),
            )
        }
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
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun findMatching(candidate: FavoriteDeviceCandidate): SavedDeviceProfile? =
        favoriteDeviceDao.getAll()
            .mapNotNull(FavoriteDeviceEntity::toSavedDeviceProfile)
            .firstOrNull { profile -> FavoriteIdentityMatcher.matches(profile, candidate) }

    // Legacy FavoriteDeviceRepository facade. It intentionally exposes only
    // explicitly favorited profiles to old callers.
    override fun observeFavorites(): Flow<List<FavoriteDevice>> =
        observeProfiles().map { profiles -> profiles.filter(SavedDeviceProfile::isFavorite) }

    override suspend fun add(favorite: FavoriteDevice): Long =
        save(favorite.copy(isFavorite = true))

    override suspend fun remove(id: Long) {
        delete(id)
    }

}
