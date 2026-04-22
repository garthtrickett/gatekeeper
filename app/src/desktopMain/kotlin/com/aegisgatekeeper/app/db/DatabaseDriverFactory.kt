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
        return driver
    }
}
