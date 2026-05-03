package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperEffect
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.currentTimeMillis
import com.aegisgatekeeper.app.domain.platformLog

suspend fun executeIntegrationEffect(
    effect: GatekeeperEffect,
    dispatch: (GatekeeperAction) -> Unit,
    effectHandler: PlatformEffectHandler,
) {
    when (effect) {
        is GatekeeperEffect.SyncBeeperChats -> {
            platformLog("Gatekeeper", "📡 Fetching Beeper chats via ContentProvider")
            effectHandler.syncBeeperChats().fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to fetch Beeper chats: $error")
                    dispatch(GatekeeperAction.BeeperSyncFailed(error))
                },
                ifRight = { chats ->
                    platformLog("Gatekeeper", "✅ Loaded ${chats.size} Beeper chats")
                    dispatch(GatekeeperAction.BeeperChatsLoaded(chats))
                },
            )
        }

        is GatekeeperEffect.ScheduleBeeperMessage -> {
            platformLog("Gatekeeper", "⚙️ Scheduling Beeper message in ${effect.delayMillis}ms")
            effectHandler.scheduleBeeperMessage(effect.message, effect.delayMillis)
        }

        else -> {}
    }
}


