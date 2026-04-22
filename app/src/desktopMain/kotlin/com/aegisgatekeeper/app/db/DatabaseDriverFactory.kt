package com.aegisgatekeeper.app.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val dataDirPath =
            when {
                osName.contains("win") -> {
                    System.getenv("APPDATA") + File.separator + "gatekeeper" + File.separator + "data"
                }
                osName.contains("mac") -> {
                    userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator + "gatekeeper" + File.separator + "data"
                }
                else -> {
                    userHome + File.separator + ".local" + File.separator + "share" + File.separator + "gatekeeper" + File.separator + "data"
                }
            }
        val dataDir = File(dataDirPath).apply { mkdirs() }
        val dbFile = File(dataDir, "gatekeeper.db")

        // Setup SQLite driver for the desktop JVM target
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            GatekeeperDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // Ignore if already created
        }
        
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}
