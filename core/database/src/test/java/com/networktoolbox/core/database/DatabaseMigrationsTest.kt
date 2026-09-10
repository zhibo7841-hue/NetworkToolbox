package com.networktoolbox.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
