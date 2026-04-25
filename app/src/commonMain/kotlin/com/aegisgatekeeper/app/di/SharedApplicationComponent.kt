package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.media.MediaDownloader
import me.tatarka.inject.annotations.Scope

@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton

interface SharedApplicationComponent {
    val tokenProvider: TokenProvider
    val mediaDownloader: MediaDownloader
}

object GlobalDI {
    lateinit var component: SharedApplicationComponent
}
