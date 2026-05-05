package com.aegisgatekeeper.app.di

import android.content.Context
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.api.PodcastIndexClient
import com.aegisgatekeeper.app.api.RssClient
import com.aegisgatekeeper.app.api.UrlMetadataClient
import com.aegisgatekeeper.app.api.YouTubeExtractor
import com.aegisgatekeeper.app.auth.AndroidTokenProvider
import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.db.AndroidSqlDriverFactory
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.db.SqlDriverFactory
import com.aegisgatekeeper.app.media.MediaDownloader
import com.aegisgatekeeper.app.sync.SyncClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
@Singleton
abstract class AndroidApplicationComponent(
    @get:Provides val context: Context,
) : SharedApplicationComponent {
    @get:Provides
    override val syncClient: SyncClient = SyncClient

    abstract val podcastIndexClient: PodcastIndexClient
    abstract val rssClient: RssClient
    abstract val urlMetadataClient: UrlMetadataClient
    abstract val youtubeExtractor: YouTubeExtractor

    @Provides
    @Singleton
    fun provideDatabase(): GatekeeperDatabase = com.aegisgatekeeper.app.db.DatabaseManager.db

    @Provides
    fun sqlDriverFactory(factory: AndroidSqlDriverFactory): SqlDriverFactory = factory

    abstract override val tokenProvider: AndroidTokenProvider

    abstract val androidBeeperClient: com.aegisgatekeeper.app.integrations.AndroidBeeperClient

    @get:Provides
    override val beeperClient: com.aegisgatekeeper.app.integrations.BeeperClient get() = androidBeeperClient

    abstract val androidEffectHandler: com.aegisgatekeeper.app.effects.AndroidEffectHandler

    @get:Provides override val effectHandler: com.aegisgatekeeper.app.effects.PlatformEffectHandler get() = androidEffectHandler

    @Provides
    @Singleton
    fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Provides
    @Singleton
    fun httpClient(): HttpClient =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
