package com.aegisgatekeeper.app.db

import app.cash.sqldelight.db.SqlDriver

interface SqlDriverFactory {
    fun createDriver(): SqlDriver
}
