package com.networktoolbox.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Favorites without touching the existing history_records table. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_devices (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                identity_type TEXT NOT NULL,
                identity_value TEXT NOT NULL,
                network_scope TEXT NOT NULL,
                last_known_ipv4 TEXT,
                last_known_display_name TEXT,
                last_known_hostname TEXT,
                last_known_mdns_name TEXT,
                last_known_upnp_name TEXT,
                mac_address TEXT,
                vendor TEXT,
                model TEXT,
                created_at INTEGER NOT NULL,
                last_seen_at INTEGER,
                is_gateway INTEGER NOT NULL,
                is_local_device INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_devices_identity " +
                "ON favorite_devices (identity_type, identity_value, network_scope)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_favorite_devices_network_scope " +
                "ON favorite_devices (network_scope)",
        )
    }
}
