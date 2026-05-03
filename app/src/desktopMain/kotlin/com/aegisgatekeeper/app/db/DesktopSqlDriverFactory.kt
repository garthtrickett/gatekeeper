package com.aegisgatekeeper.app.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import me.tatarka.inject.annotations.Inject
import java.io.File

@Inject
class DesktopSqlDriverFactory : SqlDriverFactory {
    override fun createDriver(): SqlDriver {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val dataDirPath =
            when {
                osName.contains("win") -> {
                    System.getenv("APPDATA") + File.separator + "gatekeeper" + File.separator + "data"
                }

                osName.contains("mac") -> {
                    userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator + "gatekeeper" +
                        File.separator +
                        "data"
                }

                else -> {
                    userHome + File.separator + ".local" + File.separator + "share" + File.separator + "gatekeeper" + File.separator +
                        "data"
                }
            }
        val dataDir = File(dataDirPath).apply { mkdirs() }
        val dbFile = File(dataDir, "gatekeeper.db")

        // Setup SQLite driver for the desktop JVM target
        var driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        var isSchemaValid = false
        try {
            // Test if the schema is fully up-to-date
            driver.executeQuery(null, "SELECT deepWorkStartMinutes FROM AppSettings LIMIT 1;", mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult
                    .Value(Unit)
            }, 0)
            isSchemaValid = true
        } catch (e: Exception) {
            println("Gatekeeper: Stale or missing schema detected (${e.message}). Recreating database...")
        }

        if (!isSchemaValid) {
            driver.close()
            dbFile.delete()
            driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
            GatekeeperDatabase.Schema.create(driver)
        }

        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}
