package com.aegisgatekeeper.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.services.ForegroundAppDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class GatekeeperForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        // 4. Start the Layer Alpha Heartbeat (Polling)
                startLayerAlphaHeartbeat()
        startPhaseNotificationLoop()

        // 4.5 Start VPN Service implicitly if rules exist
        serviceScope.launch {
            var vpnStarted = false
            GatekeeperStateManager.state.collect { state ->
                val hasDomainBlocks =
                    state.appGroups.any { group ->
                        group.rules.any { it is com.aegisgatekeeper.app.domain.BlockingRule.DomainBlock && it.isEnabled }
                    }
                if (hasDomainBlocks && !vpnStarted) {
                    val vpnIntent = android.net.VpnService.prepare(this@GatekeeperForegroundService)
                    if (vpnIntent == null) {
                        val intent = Intent(this@GatekeeperForegroundService, GatekeeperVpnService::class.java)
                        startService(intent)
                        vpnStarted = true
                    }
                }
            }
        }

        // 5. START_STICKY tells the OS: "If you must kill me for RAM, restart me ASAP"
        return START_STICKY
    }

        private fun startPhaseNotificationLoop() {
        serviceScope.launch {
            var lastNotifiedDay = -1
            while (isActive) {
                val state = GatekeeperStateManager.state.value
                val calendar = java.util.Calendar.getInstance()
                val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)

                val isGathering = if (state.gatheringStartMinutes <= state.gatheringEndMinutes) {
                    currentMinutes in state.gatheringStartMinutes until state.gatheringEndMinutes
                } else {
                    currentMinutes >= state.gatheringStartMinutes || currentMinutes < state.gatheringEndMinutes
                }

                if (isGathering && currentDay != lastNotifiedDay) {
                    lastNotifiedDay = currentDay
                    
                    val unresolvedVaultItems = state.vaultItems.count { !it.isResolved && !it.isDeleted }
                    val newNotifications = state.notificationDigest.size
                    
                    sendGatheringNotification(unresolvedVaultItems, newNotifications)
                }
                delay(60_000L)
            }
        }
    }

    private fun sendGatheringNotification(vaultCount: Int, digestCount: Int) {
        val channelId = "gatekeeper_phase_channel"
        val manager = getSystemService(NotificationManager::class.java)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Phase Transitions",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            if (vaultCount > 0) append("$vaultCount thoughts to process. ")
            if (digestCount > 0) append("$digestCount notifications intercepted. ")
            if (isEmpty()) append("Time to review your digital intake.")
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("The Gathering Phase has begun")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(3, notification)
    }

    private fun startLayerAlphaHeartbeat() {
        serviceScope.launch {
            while (isActive) {
                val state = GatekeeperStateManager.state.value

                // Layer Alpha polls UsageStats if Accessibility is offline or fails
                val currentApp =
                    if (!state.isLayerOmegaActive) {
                        ForegroundAppDetector.getForegroundApp(this@GatekeeperForegroundService) ?: state.activeForegroundApp
                    } else {
                        state.activeForegroundApp
                    }

                if (currentApp != null) {
                    GatekeeperStateManager.performAppValidation(this@GatekeeperForegroundService, currentApp)
                }

                // snappier polling (500ms) to reduce the "flash" on the free tier
                delay(500L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
