package com.aegisgatekeeper.backend.db

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object DatabaseFactory {
    lateinit var db: GatekeeperServerDatabase
        private set

    fun init() {
        val driverClassName = System.getenv("JDBC_DRIVER") ?: "org.sqlite.JDBC"
        val jdbcURL = System.getenv("JDBC_DATABASE_URL") ?: "jdbc:sqlite:gatekeeper_backend.db"

        val driver = if (driverClassName.contains("sqlite")) {
            JdbcSqliteDriver(jdbcURL).also {
                try {
                    GatekeeperServerDatabase.Schema.create(it)
                } catch (e: Exception) {
                    // Ignore if already created
                }
            }
        } else {
            val user = System.getenv("JDBC_DATABASE_USER") ?: "postgres"
            val password = System.getenv("JDBC_DATABASE_PASSWORD") ?: "password"
            val dataSource = createHikariDataSource(jdbcURL, driverClassName, user, password)
            dataSource.asJdbcDriver().also {
                try {
                    GatekeeperServerDatabase.Schema.create(it)
                } catch (e: Exception) {
                    // Ignore if already created
                }
            }
        }

        db = GatekeeperServerDatabase(driver)
    }

    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String,
        pass: String
    ) = HikariDataSource(HikariConfig().apply {
        driverClassName = driver
        jdbcUrl = url
        username = user
        password = pass
        maximumPoolSize = 3
        isAutoCommit = true // SQLDelight handles its own transactions
        validate()
    })
}
