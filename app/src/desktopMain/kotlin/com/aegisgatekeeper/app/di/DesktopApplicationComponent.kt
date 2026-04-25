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

    abstract override val syncClient: SyncClient

    @get:Provides
    abstract override val tokenProvider: DesktopTokenProvider

    @Provides
    fun sqlDriverFactory(factory: DesktopSqlDriverFactory): SqlDriverFactory = factory
}
