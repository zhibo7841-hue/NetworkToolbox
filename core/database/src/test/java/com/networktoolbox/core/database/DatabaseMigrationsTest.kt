package com.networktoolbox.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationsTest {
    @Test
    fun `favorite migration creates only additive table and indexes`() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args?.firstOrNull()?.toString().orEmpty()
            defaultValue(method.returnType)
        } as SupportSQLiteDatabase

        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        MIGRATION_1_2.migrate(database)

        assertEquals(3, statements.size)
        assertTrue(statements.first().contains("CREATE TABLE IF NOT EXISTS favorite_devices"))
        assertTrue(statements.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS") })
        assertTrue(statements.any { it.contains("CREATE INDEX IF NOT EXISTS") })
        assertFalse(statements.any { it.contains("DROP", ignoreCase = true) })
        assertFalse(statements.any { it.contains("DELETE", ignoreCase = true) })
    }

    @Test
    fun `saved profile migration adds explicit favorite and custom name fields`() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args?.firstOrNull()?.toString().orEmpty()
            defaultValue(method.returnType)
        } as SupportSQLiteDatabase

        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
        MIGRATION_2_3.migrate(database)

        assertEquals(4, statements.size)
        assertTrue(statements.any { it.contains("ADD COLUMN custom_name") })
        assertTrue(statements.any { it.contains("ADD COLUMN is_favorite") })
        assertTrue(statements.any { it.contains("ADD COLUMN updated_at") })
        assertTrue(statements.any { it.contains("updated_at = created_at") })
        assertFalse(statements.any { it.contains("DROP", ignoreCase = true) })
        assertFalse(statements.any { it.contains("DELETE", ignoreCase = true) })
    }

    @Test
    fun `legacy v2 favorite rows map to profiles without changing history mapping`() {
        val legacyRows = listOf(
            legacyFavorite(id = 1L, ip = "10.0.1.10", createdAt = 10L),
            legacyFavorite(id = 2L, ip = "10.0.1.11", createdAt = 20L),
        )

        val profiles = legacyRows.mapNotNull(FavoriteDeviceEntity::toSavedDeviceProfile)

        assertEquals(listOf(1L, 2L), profiles.map { it.id })
        assertTrue(profiles.all { it.isFavorite })
        assertTrue(profiles.all { it.customName == null })
        assertEquals(listOf("10.0.1.10", "10.0.1.11"), profiles.map { it.lastKnownIpv4 })
        assertEquals(listOf(10L, 20L), profiles.map { it.createdAt })
        assertEquals(listOf(10L, 20L), profiles.map { it.updatedAt })

        val history = HistoryEntity(
            id = 7L,
            timestamp = 30L,
            type = "PING",
            title = "10.0.1.10",
            summary = "Completed",
            detailJson = "{}",
        )
        val historyRecord = history.toRecord()
        assertEquals(7L, historyRecord.id)
        assertEquals("10.0.1.10", historyRecord.title)
        assertEquals("{}", historyRecord.detailJson)
        assertNull(profiles.first().customName)
    }

    private fun legacyFavorite(id: Long, ip: String, createdAt: Long) = FavoriteDeviceEntity(
        id = id,
        identityType = "NETWORK_IP",
        identityValue = ip,
        networkScope = "scope-a",
        lastKnownIpv4 = ip,
        lastKnownDisplayName = "device-$id",
        lastKnownHostname = null,
        lastKnownMdnsName = null,
        lastKnownUpnpName = null,
        macAddress = null,
        vendor = null,
        model = null,
        createdAt = createdAt,
        lastSeenAt = createdAt,
        isGateway = 0,
        isLocalDevice = 0,
    )

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
