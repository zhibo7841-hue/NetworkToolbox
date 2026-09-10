package com.networktoolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDeviceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteDeviceEntity): Long

    @Query("SELECT * FROM favorite_devices ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<FavoriteDeviceEntity>

    @Query("SELECT * FROM favorite_devices ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<FavoriteDeviceEntity>>

    @Query(
        "SELECT * FROM favorite_devices " +
            "WHERE identity_type = :identityType " +
            "AND identity_value = :identityValue " +
            "AND network_scope = :networkScope LIMIT 1",
    )
    suspend fun findByIdentity(
        identityType: String,
        identityValue: String,
        networkScope: String,
    ): FavoriteDeviceEntity?

    @Query(
        "UPDATE favorite_devices SET " +
            "last_known_ipv4 = :lastKnownIpv4, " +
            "last_known_display_name = :lastKnownDisplayName, " +
            "last_known_hostname = :lastKnownHostname, " +
            "last_known_mdns_name = :lastKnownMdnsName, " +
            "last_known_upnp_name = :lastKnownUpnpName, " +
            "mac_address = :macAddress, " +
            "vendor = :vendor, " +
            "model = :model, " +
            "last_seen_at = :lastSeenAt, " +
            "is_gateway = :isGateway, " +
            "is_local_device = :isLocalDevice " +
            "WHERE id = :id",
    )
    suspend fun updateObserved(
        id: Long,
        lastKnownIpv4: String?,
        lastKnownDisplayName: String?,
        lastKnownHostname: String?,
        lastKnownMdnsName: String?,
        lastKnownUpnpName: String?,
        macAddress: String?,
        vendor: String?,
        model: String?,
        lastSeenAt: Long,
        isGateway: Int,
        isLocalDevice: Int,
    )

    @Query("DELETE FROM favorite_devices WHERE id = :id")
    suspend fun deleteById(id: Long)
}
