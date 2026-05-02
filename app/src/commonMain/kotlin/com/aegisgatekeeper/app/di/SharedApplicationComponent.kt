package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.integrations.BeeperClient
import com.aegisgatekeeper.app.sync.SyncClient
import me.tatarka.inject.annotations.Scope

@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton

interface SharedApplicationComponent {
    val tokenProvider: TokenProvider
    val syncClient: SyncClient
    val beeperClient: BeeperClient
}

