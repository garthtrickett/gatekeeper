package com.aegisgatekeeper.app.integrations

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.di.GlobalDI
import com.aegisgatekeeper.app.domain.GatekeeperAction

class MessageDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        Log.i("Gatekeeper", "⚙️ MessageDeliveryWorker: Attempting to send message $messageId")

        val db = DatabaseManager.db
        val message = db.scheduledMessageQueries.selectAll().executeAsList().find { it.id == messageId }
        
        if (message == null) {
            Log.w("Gatekeeper", "❌ MessageDeliveryWorker: Message $messageId not found in DB")
            return Result.failure()
        }

        if (message.status != com.aegisgatekeeper.app.domain.MessageStatus.PENDING) {
            Log.i("Gatekeeper", "⚙️ MessageDeliveryWorker: Message $messageId is no longer PENDING (status: ${message.status})")
            return Result.success()
        }

        val beeperClient = GlobalDI.component.beeperClient
        val result = beeperClient.sendMessage(message.beeperRoomId, message.messageText)

        return result.fold(
            ifLeft = { error ->
                Log.e("Gatekeeper", "❌ MessageDeliveryWorker: Failed to send $messageId: $error")
                GatekeeperStateManager.dispatch(GatekeeperAction.MessageFailed(messageId, error))
                Result.retry()
            },
            ifRight = {
                Log.i("Gatekeeper", "✅ MessageDeliveryWorker: Successfully sent message $messageId to Beeper")
                GatekeeperStateManager.dispatch(GatekeeperAction.MessageDelivered(messageId))
                Result.success()
            }
        )
    }
}
