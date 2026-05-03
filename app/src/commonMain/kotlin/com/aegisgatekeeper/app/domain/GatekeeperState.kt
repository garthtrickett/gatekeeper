package com.aegisgatekeeper.app.domain

/**
 * The strict, immutable representation of the app's current state.
 * Always use .copy() to update values. Never use var.
 */
data class GatekeeperState(
    val interception: InterceptionState = InterceptionState(),
    val data: DataState = DataState(),
    val media: MediaState = MediaState(),
    val sync: SyncAndIntegrationState = SyncAndIntegrationState(),
) {
    val isDualMoatEnabled: Boolean
        get() = interception.isDualMoatEnabled
}
