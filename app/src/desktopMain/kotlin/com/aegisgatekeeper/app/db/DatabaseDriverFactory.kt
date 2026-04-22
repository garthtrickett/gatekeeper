package com.aegisgatekeeper.app.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aegisgatekeeper.app.db.GatekeeperDatabase

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        // Setup SQLite driver for the desktop JVM target
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY + "gatekeeper.db")
        try {
            GatekeeperDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // Ignore if already created
        }
        driver.execute(
            null,
            "CREATE TABLE IF NOT EXISTS MediaPosition (mediaId TEXT NOT NULL PRIMARY KEY, positionSeconds REAL NOT NULL);",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE IF NOT EXISTS AlternativeActivity (id TEXT NOT NULL PRIMARY KEY, description TEXT NOT NULL, createdAtTimestamp INTEGER NOT NULL);",
            0,
        )
        return driver
    }
}
