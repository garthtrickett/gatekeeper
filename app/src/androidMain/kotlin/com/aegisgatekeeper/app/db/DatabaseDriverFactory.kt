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
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        try {
                            super.onUpgrade(db, oldVersion, newVersion)
                        } catch (e: Exception) {
                            if (com.aegisgatekeeper.app.BuildConfig.DEBUG) {
                                android.util.Log.i("Gatekeeper", "DB: Migration failed (expected during dev). Destructively recreating tables...")
                                db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                                    val tables = mutableListOf<String>()
                                    while (cursor.moveToNext()) {
                                        tables.add(cursor.getString(0))
                                    }

                                    db.execSQL("PRAGMA foreign_keys=OFF;")
                                    for (tableName in tables) {
                                        if (tableName != "android_metadata" && tableName != "sqlite_sequence") {
                                            db.execSQL("DROP TABLE IF EXISTS $tableName")
                                        }
                                    }
                                    db.execSQL("PRAGMA foreign_keys=ON;")
                                }
                                super.onCreate(db)
                            } else {
                                // PRODUCTION: Never wipe user data. Throw to Crashlytics/Sentry.
                                android.util.Log.e("Gatekeeper", "CRITICAL: Database migration failed in Production!", e)
                                throw e
                            }
                        }
                    }
                },
        )
}
