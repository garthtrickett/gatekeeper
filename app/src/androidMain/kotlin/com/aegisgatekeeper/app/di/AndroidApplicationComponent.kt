package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.api.PodcastIndexClient
import com.aegisgatekeeper.app.api.RssClient
import com.aegisgatekeeper.app.api.UrlMetadataClient
import com.aegisgatekeeper.app.auth.AndroidTokenProvider
import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.db.AndroidSqlDriverFactory
import com.aegisgatekeeper.app.db.DatabaseProvider
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.db.SqlDriverFactory
import com.aegisgatekeeper.app.effects.DatabaseEffectHandler
import com.aegisgatekeeper.app.effects.MediaEffectHandler
import com.aegisgatekeeper.app.effects.SyncEffectHandler
import com.aegisgatekeeper.app.media.AndroidMediaDownloader
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
    @get:Provides val context: Context
) : SharedApplicationComponent {

    abstract override val gatekeeperStateManager: GatekeeperStateManager
    abstract override val syncClient: SyncClient

    @get:Provides
    abstract val podcastIndexClient: PodcastIndexClient
    @get:Provides
    abstract val rssClient: RssClient
    @get:Provides
    abstract val urlMetadataClient: UrlMetadataClient
    @get:Provides
    abstract val dbEffectHandler: DatabaseEffectHandler
    @get:Provides
    abstract val syncEffectHandler: SyncEffectHandler
    @get:Provides
    abstract val mediaEffectHandler: MediaEffectHandler

    @get:Provides
    abstract val dbProvider: DatabaseProvider

    @Provides
    @Singleton
    fun provideDatabase(provider: DatabaseProvider): GatekeeperDatabase = provider.db

    @Provides
    fun sqlDriverFactory(factory: AndroidSqlDriverFactory): SqlDriverFactory = factory

    @get:Provides
    override val tokenProvider: AndroidTokenProvider

    @get:Provides
    override val mediaDownloader: AndroidMediaDownloader

    @Provides
    @Singleton
    fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
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
