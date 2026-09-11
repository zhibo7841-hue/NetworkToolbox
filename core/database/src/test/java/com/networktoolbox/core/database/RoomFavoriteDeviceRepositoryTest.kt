package com.networktoolbox.core.database

import com.networktoolbox.core.common.favorites.FavoriteDevice
import com.networktoolbox.core.common.favorites.FavoriteDeviceCandidate
import com.networktoolbox.core.common.favorites.FavoriteDeviceObservation
import com.networktoolbox.core.common.favorites.FavoriteDeviceRepository
import com.networktoolbox.core.common.favorites.FavoriteIdentityType
import com.networktoolbox.core.common.favorites.SavedDeviceRepository
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

    @Test
    fun `custom name and favorite state are independent`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite())

        repository.setCustomName(id, "客厅网关")
        repository.setFavorite(id, false)

        val saved = repository.observeProfiles().first().single()
        assertEquals("客厅网关", saved.customName)
        assertEquals(false, saved.isFavorite)

        repository.setFavorite(id, true)
        assertTrue(repository.observeProfiles().first().single().isFavorite)

        repository.setCustomName(id, null)
        val restored = repository.observeProfiles().first().single()
        assertEquals(null, restored.customName)
        assertTrue(restored.isFavorite)
    }

    @Test
    fun `nonfavorite custom profile survives and is removed when both values clear`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(isFavorite = false, customName = "HomeLab NAS"))

        assertEquals(1, repository.observeProfiles().first().size)
        assertEquals(0, repository.observeFavorites().first().size)

        repository.setCustomName(id, null)

        assertTrue(repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun `saved profile keeps custom name while observation metadata is refreshed`() = runBlocking {
        val repository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val id = repository.save(favorite().copy(customName = "主路由"))

        repository.updateLastObserved(
            id,
            FavoriteDeviceObservation(
                lastKnownIpv4 = "10.0.1.99",
                lastKnownDisplayName = "router.local",
                lastKnownHostname = "router.local",
                lastKnownMdnsName = null,
                lastKnownUpnpName = null,
                macAddress = null,
                vendor = "Example",
                model = "Router",
                lastSeenAt = 99L,
                isGateway = true,
                isLocalDevice = false,
            ),
        )

        val saved = repository.observeProfiles().first().single()
        assertEquals("主路由", saved.customName)
        assertEquals("10.0.1.99", saved.lastKnownIpv4)
        assertEquals(99L, saved.lastSeenAt)
    }

    @Test
    fun `saving same identity does not create duplicate profile`() = runBlocking {
        val repository: SavedDeviceRepository = RoomFavoriteDeviceRepository(FakeFavoriteDeviceDao())
        val firstId = repository.save(favorite())
        val secondId = repository.save(favorite().copy(id = 0L, customName = "same device"))

        assertEquals(firstId, secondId)
        assertEquals(1, repository.observeProfiles().first().size)
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

    override suspend fun findById(id: Long): FavoriteDeviceEntity? =
        entities.firstOrNull { it.id == id }

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
        updatedAt: Long,
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
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateFavorite(id: Long, isFavorite: Int, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            isFavorite = isFavorite,
            updatedAt = updatedAt,
        )
        publish()
    }

    override suspend fun updateCustomName(id: Long, customName: String?, updatedAt: Long) {
        val index = entities.indexOfFirst { it.id == id }
        if (index < 0) return
        entities[index] = entities[index].copy(
            customName = customName,
            updatedAt = updatedAt,
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
