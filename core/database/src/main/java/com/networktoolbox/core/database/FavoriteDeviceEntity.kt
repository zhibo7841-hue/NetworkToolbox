package com.networktoolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteIdentityType

@Entity(
    tableName = "favorite_devices",
    indices = [
        Index(
            value = ["identity_type", "identity_value", "network_scope"],
            unique = true,
        ),
        Index(value = ["network_scope"]),
    ],
)
data class FavoriteDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "identity_type")
    val identityType: String,
    @ColumnInfo(name = "identity_value")
    val identityValue: String,
    @ColumnInfo(name = "network_scope")
    val networkScope: String,
    @ColumnInfo(name = "last_known_ipv4")
    val lastKnownIpv4: String?,
    @ColumnInfo(name = "last_known_display_name")
    val lastKnownDisplayName: String?,
    @ColumnInfo(name = "last_known_hostname")
    val lastKnownHostname: String?,
    @ColumnInfo(name = "last_known_mdns_name")
    val lastKnownMdnsName: String?,
    @ColumnInfo(name = "last_known_upnp_name")
    val lastKnownUpnpName: String?,
    @ColumnInfo(name = "mac_address")
    val macAddress: String?,
    val vendor: String?,
    val model: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long?,
    @ColumnInfo(name = "is_gateway")
    val isGateway: Int,
    @ColumnInfo(name = "is_local_device")
    val isLocalDevice: Int,
)

fun FavoriteDevice.toEntity(): FavoriteDeviceEntity = FavoriteDeviceEntity(
    id = id,
    identityType = identityType.name,
    identityValue = identityValue,
    networkScope = networkScope,
    lastKnownIpv4 = lastKnownIpv4,
    lastKnownDisplayName = lastKnownDisplayName,
    lastKnownHostname = lastKnownHostname,
    lastKnownMdnsName = lastKnownMdnsName,
    lastKnownUpnpName = lastKnownUpnpName,
    macAddress = macAddress,
    vendor = vendor,
    model = model,
    createdAt = createdAt,
    lastSeenAt = lastSeenAt,
    isGateway = if (isGateway) 1 else 0,
    isLocalDevice = if (isLocalDevice) 1 else 0,
)

fun FavoriteDeviceEntity.toFavoriteDevice(): FavoriteDevice? = runCatching {
    FavoriteDevice(
        id = id,
        identityType = FavoriteIdentityType.valueOf(identityType),
        identityValue = identityValue,
        networkScope = networkScope,
        lastKnownIpv4 = lastKnownIpv4,
        lastKnownDisplayName = lastKnownDisplayName,
        lastKnownHostname = lastKnownHostname,
        lastKnownMdnsName = lastKnownMdnsName,
        lastKnownUpnpName = lastKnownUpnpName,
        macAddress = macAddress,
        vendor = vendor,
        model = model,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        isGateway = isGateway != 0,
        isLocalDevice = isLocalDevice != 0,
    )
}.getOrNull()
