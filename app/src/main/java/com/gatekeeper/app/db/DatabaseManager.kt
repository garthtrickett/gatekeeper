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

    private val contentSourceAdapter =
        object : ColumnAdapter<ContentSource, String> {
            override fun decode(databaseValue: String): ContentSource = ContentSource.valueOf(databaseValue)

            override fun encode(value: ContentSource): String = value.name
        }

    private val contentTypeAdapter =
        object : ColumnAdapter<ContentType, String> {
            override fun decode(databaseValue: String): ContentType = ContentType.valueOf(databaseValue)

            override fun encode(value: ContentType): String = value.name
        }

    val db: GatekeeperDatabase by lazy {
        val driver = AndroidSqliteDriver(GatekeeperDatabase.Schema, App.instance, "gatekeeper.db")
        GatekeeperDatabase(
            driver = driver,
            SessionLogAdapter =
                SessionLog.Adapter(
                    emotionAdapter = emotionAdapter,
                ),
            ContentItemAdapter =
                ContentItem.Adapter(
                    sourceAdapter = contentSourceAdapter,
                    typeAdapter = contentTypeAdapter,
                ),
        )
    }
}
