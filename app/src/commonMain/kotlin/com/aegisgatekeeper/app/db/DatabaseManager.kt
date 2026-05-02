package com.aegisgatekeeper.app.db

import app.cash.sqldelight.ColumnAdapter
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.Emotion

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

    private val downloadStatusAdapter =
        object : ColumnAdapter<com.aegisgatekeeper.app.domain.DownloadStatus, String> {
            override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.DownloadStatus =
                com.aegisgatekeeper.app.domain.DownloadStatus
                    .valueOf(databaseValue)

            override fun encode(value: com.aegisgatekeeper.app.domain.DownloadStatus): String = value.name
        }

    private val frictionGameAdapter =
        object : ColumnAdapter<com.aegisgatekeeper.app.domain.FrictionGame, String> {
            override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.FrictionGame =
                com.aegisgatekeeper.app.domain.FrictionGame
                    .valueOf(databaseValue)

            override fun encode(value: com.aegisgatekeeper.app.domain.FrictionGame): String = value.name
        }

    private val ruleCombinatorAdapter =
        object : ColumnAdapter<com.aegisgatekeeper.app.domain.RuleCombinator, String> {
            override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.RuleCombinator =
                com.aegisgatekeeper.app.domain.RuleCombinator
                    .valueOf(databaseValue)

            override fun encode(value: com.aegisgatekeeper.app.domain.RuleCombinator): String = value.name
        }

    private val messageStatusAdapter =
        object : ColumnAdapter<com.aegisgatekeeper.app.domain.MessageStatus, String> {
            override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.MessageStatus =
                com.aegisgatekeeper.app.domain.MessageStatus
                    .valueOf(databaseValue)

            override fun encode(value: com.aegisgatekeeper.app.domain.MessageStatus): String = value.name
        }

    lateinit var db: GatekeeperDatabase

    fun init(driverFactory: SqlDriverFactory) {
        db =
            GatekeeperDatabase(
                driver = driverFactory.createDriver(),
                AppGroupAdapter =
                    AppGroup.Adapter(
                        ruleCombinatorAdapter = ruleCombinatorAdapter,
                    ),
                AppSettingsAdapter =
                    AppSettings.Adapter(
                        activeFrictionGameAdapter = frictionGameAdapter,
                    ),
                SessionLogAdapter =
                    SessionLog.Adapter(
                        emotionAdapter = emotionAdapter,
                    ),
                ContentItemAdapter =
                    ContentItem.Adapter(
                        sourceAdapter = contentSourceAdapter,
                        typeAdapter = contentTypeAdapter,
                        downloadStatusAdapter = downloadStatusAdapter,
                    ),
                ScheduledMessageAdapter =
                    ScheduledMessage.Adapter(
                        statusAdapter = messageStatusAdapter,
                    ),
            )
    }
}
