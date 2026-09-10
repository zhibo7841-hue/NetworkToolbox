package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFavoriteDeviceRepositoryTest {
    @Test
    fun `add and observe map favorite entity`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val favorite = favorite()

        val id = repository.add(favorite)
        val saved = repository.observeFavorites().first().single()

        assertEquals(1L, id)
        assertEquals(id, saved.id)
        assertEquals(favorite.identityType, saved.identityType)
        assertEquals(favorite.identityValue, saved.identityValue)
    }

    @Test
    fun `duplicate add returns existing id without duplicating`() = runBlocking {
        val dao = FakeFavoriteDeviceDao()
        val repository = RoomFavoriteDeviceRepository(dao)
        val favorite = favorite()

        val first = repository.add(favorite)
        val second = repository.add(favorite.copy(id = 0L))

        assertEquals(first, second)
        assertEquals(1, repository.observeFavorites().first().size)
    }

    @Test
    fun `remove deletes requested favorite only`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val first = repository.add(favorite(ip = "10.0.1.20"))
        repository.add(favorite(ip = "10.0.1.21", identityValue = "10.0.1.21"))

        repository.remove(first)

        assertEquals(listOf("10.0.1.21"), repository.observeFavorites().first().map { it.identityValue })
    }

    @Test
    fun `update last observed keeps favorite and updates metadata`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.add(favorite())

        repository.updateLastObserved(
            id,
            FavoriteDeviceObservation(
                lastKnownIpv4 = "10.0.1.25",
                lastKnownDisplayName = "Living Room Hub",
                lastKnownHostname = "hub.local",
                lastKnownMdnsName = "Hub",
                lastKnownUpnpName = null,
                macAddress = "aa-bb-cc-dd-ee-ff",
                vendor = "Example",
                model = "Hub 2",
                lastSeenAt = 99L,
                isGateway = true,
                isLocalDevice = false,
            ),
        )

        val saved = repository.observeFavorites().first().single()
        assertEquals(id, saved.id)
        assertEquals("10.0.1.25", saved.lastKnownIpv4)
        assertEquals("Living Room Hub", saved.lastKnownDisplayName)
        assertEquals(99L, saved.lastSeenAt)
        assertTrue(saved.isGateway)
        assertEquals("AA:BB:CC:DD:EE:FF", saved.macAddress)
    }

    @Test
    fun `find matching respects network scope`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        repository.add(favorite(scope = "scope-a", identityValue = "10.0.1.20"))

        assertNotNull(
            repository.findMatching(
                FavoriteDeviceCandidate("scope-a", "10.0.1.20", macAddress = null),
            ),
        )
        assertNull(
            repository.findMatching(
                FavoriteDeviceCandidate("scope-b", "10.0.1.20", macAddress = null),
            ),
        )
    }

    @Test
    fun `new repository instance reads the same local dao state`() = runBlocking {
        val dao = FakeFavoriteDeviceDao()
        val firstRepository: FavoriteDeviceRepository = RoomFavoriteDeviceRepository(dao)
        firstRepository.add(favorite())

        val secondRepository: FavoriteDeviceRepository = RoomFavoriteDeviceRepository(dao)

        assertEquals(1, secondRepository.observeFavorites().first().size)
    }

    private fun favorite(
        ip: String = "10.0.1.20",
        identityValue: String = "AA:BB:CC:DD:EE:FF",
        scope: String = "scope-a",
    ) = FavoriteDevice(
        identityType = if (identityValue.contains(":")) FavoriteIdentityType.MAC else FavoriteIdentityType.NETWORK_IP,
        identityValue = identityValue,
        networkScope = scope,
        lastKnownIpv4 = ip,
        lastKnownDisplayName = null,
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = identityValue.takeIf { it.contains(":") },
        vendor = null,
        model = null,
        createdAt = 1L,
        lastSeenAt = 1L,
    )
}

private class FakeFavoriteDeviceDao : FavoriteDeviceDao {
    private val entities = mutableListOf<FavoriteDeviceEntity>()
    private val flow = MutableStateFlow<List<FavoriteDeviceEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(favorite: FavoriteDeviceEntity): Long {
        val duplicate = entities.firstOrNull {
            it.identityType == favorite.identityType &&
                it.identityValue == favorite.identityValue &&
                it.networkScope == favorite.networkScope
        }
        if (duplicate != null) return -1L
        val stored = favorite.copy(id = if (favorite.id > 0L) favorite.id else nextId++)
        entities += stored
        publish()
        return stored.id
    }

    override suspend fun getAll(): List<FavoriteDeviceEntity> = entities
        .sortedWith(compareBy<FavoriteDeviceEntity> { it.createdAt }.thenBy { it.id })

    override fun observeAll(): Flow<List<FavoriteDeviceEntity>> = flow

    override suspend fun findByIdentity(
        identityType: String,
        identityValue: String,
        networkScope: String,
    ): FavoriteDeviceEntity? = entities.firstOrNull {
        it.identityType == identityType &&
            it.identityValue == identityValue &&
            it.networkScope == networkScope
    }

    override suspend fun updateObserved(
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
    ) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        val existing = entities[index]
        entities[index] = existing.copy(
            lastKnownIpv4 = lastKnownIpv4,
            lastKnownDisplayName = lastKnownDisplayName,
            lastKnownHostname = lastKnownHostname,
            lastKnownMdnsName = lastKnownMdnsName,
            lastKnownUpnpName = lastKnownUpnpName,
            macAddress = macAddress,
            vendor = vendor,
            model = model,
            lastSeenAt = lastSeenAt,
            isGateway = isGateway,
            isLocalDevice = isLocalDevice,
        )
        publish()
    }

    override suspend fun deleteById(id: Long) {
        entities.removeAll { it.id == id }
        publish()
    }

    private fun publish() {
        flow.value = entities.toList()
    }
}
