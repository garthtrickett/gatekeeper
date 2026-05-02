package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.auth.DesktopTokenProvider
import com.aegisgatekeeper.app.db.DesktopSqlDriverFactory
import com.aegisgatekeeper.app.db.SqlDriverFactory
import com.aegisgatekeeper.app.sync.SyncClient
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
@Singleton
abstract class DesktopApplicationComponent : SharedApplicationComponent {
    @get:Provides
    override val syncClient: SyncClient = SyncClient

    abstract override val tokenProvider: DesktopTokenProvider

    abstract val desktopBeeperClient: com.aegisgatekeeper.app.integrations.DesktopBeeperClient

    @get:Provides
    override val beeperClient: com.aegisgatekeeper.app.integrations.BeeperClient get() = desktopBeeperClient

    @Provides
    fun sqlDriverFactory(factory: DesktopSqlDriverFactory): SqlDriverFactory = factory
}
