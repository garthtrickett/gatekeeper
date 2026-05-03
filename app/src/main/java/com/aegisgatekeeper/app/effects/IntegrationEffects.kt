@file:JvmName("DeletedIntegrationEffects")

package com.aegisgatekeeper.app.effects

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.di.GlobalDI
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.integrations.MessageDeliveryWorker
import java.util.concurrent.TimeUnit

fun deletedHandleIntegrationEffects_java() {}
    action: GatekeeperAction,
    newState: GatekeeperState,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (action) {
        is GatekeeperAction.RequestBeeperSync -> {
            Log.i("Gatekeeper", "📡 Fetching Beeper chats via ContentProvider")
            val result = GlobalDI.component.beeperClient.getChats()
            result.fold(
                ifLeft = { error ->
                    Log.e("Gatekeeper", "❌ Failed to fetch Beeper chats: $error")
                    dispatch(GatekeeperAction.BeeperSyncFailed(error))
                },
                ifRight = { chats ->
                    Log.i("Gatekeeper", "✅ Loaded ${chats.size} Beeper chats")
                    dispatch(GatekeeperAction.BeeperChatsLoaded(chats))
                },
            )
        }

        is GatekeeperAction.ScheduleMessage -> {
            val delayMillis = action.message.scheduledTimestamp - System.currentTimeMillis()
            val actualDelay = maxOf(0L, delayMillis)
            Log.i("Gatekeeper", "⚙️ Scheduling Beeper message in ${actualDelay}ms")

            val data =
                Data
                    .Builder()
                    .putString("messageId", action.message.id)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<MessageDeliveryWorker>()
                    .setInitialDelay(actualDelay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()

            WorkManager.getInstance(App.instance).enqueue(workRequest)
        }

        else -> {}
    }
}
