package com.gatekeeper.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GatekeeperForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val channelId = "gatekeeper_core_channel"

        // 1. Create the Notification Channel (Required for Android 8+)
        val channel =
            NotificationChannel(
                channelId,
                "Gatekeeper Status",
                NotificationManager.IMPORTANCE_LOW, // Low priority so it doesn't buzz/ring
            )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        // 2. Build the un-swipeable persistent notification
        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Gatekeeper is Active")
                .setContentText("Guarding your attention at the OS level.")
                .setSmallIcon(android.R.drawable.ic_secure) // Native Android lock icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

        // 3. Anchor the service to the notification to prevent OS battery kills
        startForeground(1, notification)

        // 4. START_STICKY tells the OS: "If you must kill me for RAM, restart me ASAP"
        return START_STICKY
    }
}
