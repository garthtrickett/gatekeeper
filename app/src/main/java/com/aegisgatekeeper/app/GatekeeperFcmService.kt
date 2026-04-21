package com.aegisgatekeeper.app

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aegisgatekeeper.app.sync.SyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GatekeeperFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Inspect the data payload for the silent poke
        if (message.data["action"] == "sync_poke") {
            Log.i("Gatekeeper", "📡 FCM: Received silent 'sync_poke'. Triggering SyncWorker.")

            // Enqueue an immediate, one-time SyncWorker using WorkManager
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "fcm-sync-poke",
                ExistingWorkPolicy.REPLACE, // We want the latest sync to run
                syncRequest,
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("Gatekeeper", "📡 FCM: Received new token.")
        // Step 2 will handle sending this token to the backend
    }
}
