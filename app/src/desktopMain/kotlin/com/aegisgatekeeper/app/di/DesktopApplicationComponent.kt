package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.auth.DesktopTokenProvider
import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.media.DesktopMediaDownloader
import com.aegisgatekeeper.app.media.MediaDownloader
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
@Singleton
abstract class DesktopApplicationComponent : SharedApplicationComponent {

    @Provides
    fun tokenProvider(impl: DesktopTokenProvider): TokenProvider = impl

    @Provides
    fun mediaDownloader(impl: DesktopMediaDownloader): MediaDownloader = impl
}
