package com.aegisgatekeeper.app.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.db.GatekeeperDatabase

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = GatekeeperDatabase.Schema,
            context = App.instance,
            name = "gatekeeper.db",
            callback =
                object : AndroidSqliteDriver.Callback(GatekeeperDatabase.Schema) {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                        // Development fallback to ensure the new table exists without requiring a clean install
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS MediaPosition (mediaId TEXT NOT NULL PRIMARY KEY, positionSeconds REAL NOT NULL);",
                        )
                    }
                },
        )
}
