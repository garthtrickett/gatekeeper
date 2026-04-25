package com.aegisgatekeeper.app.di

import android.content.Context
import com.aegisgatekeeper.app.auth.AndroidTokenProvider
import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.media.AndroidMediaDownloader
import com.aegisgatekeeper.app.media.MediaDownloader
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
abstract class AndroidApplicationComponent(
    @get:Provides val context: Context
) : SharedApplicationComponent {

    @Provides
    fun tokenProvider(impl: AndroidTokenProvider): TokenProvider = impl

    @Provides
    fun mediaDownloader(impl: AndroidMediaDownloader): MediaDownloader = impl

    @Provides
    fun sqlDriverFactory(impl: AndroidSqlDriverFactory): SqlDriverFactory = impl

    @Provides
    @Singleton
    fun provideDatabase(provider: DatabaseProvider): GatekeeperDatabase = provider.db
}
