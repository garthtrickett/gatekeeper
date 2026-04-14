package com.gatekeeper.app.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gatekeeper.app.App
import com.gatekeeper.app.domain.Emotion

object DatabaseManager {
    /**
     * A custom SQLDelight adapter to convert between the `Emotion` enum
     * and its TEXT representation in the database.
     */
    private val emotionAdapter =
        object : ColumnAdapter<Emotion, String> {
            override fun decode(databaseValue: String): Emotion = Emotion.valueOf(databaseValue)

            override fun encode(value: Emotion): String = value.name
        }

    val db: GatekeeperDatabase by lazy {
        val driver = AndroidSqliteDriver(GatekeeperDatabase.Schema, App.instance, "gatekeeper.db")
        GatekeeperDatabase(
            driver = driver,
            SessionLogAdapter =
                SessionLog.Adapter(
                    emotionAdapter = emotionAdapter,
                ),
        )
    }
}
