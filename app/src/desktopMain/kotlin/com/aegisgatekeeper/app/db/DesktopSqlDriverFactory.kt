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
        try {
            val currentVersion = GatekeeperDatabase.Schema.version
            val userVersion = driver.executeQuery(null, "PRAGMA user_version;", mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) {
                        cursor.getLong(0) ?: 0L
                    } else {
                        0L
                    }
                )
            }, 0).value

            if (userVersion == 0L) {
                try {
                    GatekeeperDatabase.Schema.create(driver)
                    driver.execute(null, "PRAGMA user_version = $currentVersion;", 0)
                } catch (e: Exception) {
                    println("Gatekeeper: Error creating schema. Likely old DB. Wiping... ${e.message}")
                    driver.close()
                    dbFile.delete()
                    driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
                    GatekeeperDatabase.Schema.create(driver)
                    driver.execute(null, "PRAGMA user_version = $currentVersion;", 0)
                }
            } else if (userVersion < currentVersion) {
                try {
                    GatekeeperDatabase.Schema.migrate(driver, userVersion, currentVersion)
                    driver.execute(null, "PRAGMA user_version = $currentVersion;", 0)
                } catch (e: Exception) {
                    println("Gatekeeper: Migration failed. Wiping... ${e.message}")
                    driver.close()
                    dbFile.delete()
                    driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
                    GatekeeperDatabase.Schema.create(driver)
                    driver.execute(null, "PRAGMA user_version = $currentVersion;", 0)
                }
            }
        } catch (e: Exception) {
            println("Gatekeeper: DB Setup Error - ${e.message}")
        }

        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}
