package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperEffect
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.platformLog

fun executeSyncAndAuthEffect(effect: GatekeeperEffect) {
    when (effect) {
        is GatekeeperEffect.SaveToken -> {
            platformLog("Gatekeeper", "✅ LoginSuccess: Token received")
        }

        is GatekeeperEffect.ClearToken -> {
            platformLog("Gatekeeper", "🚪 Logout: Clearing tokens")
        }

        is GatekeeperEffect.RequestMagicLink -> {
            platformLog("Gatekeeper", "🌐 API: Requesting magic link for ${effect.email}")
        }

        else -> {}
    }
}


