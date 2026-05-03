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
            val notifiedCheckIns = mutableSetOf<String>()
            while (isActive) {
                val state = GatekeeperStateManager.state.value
                val calendar = java.util.Calendar.getInstance()
                val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)

                val isGathering =
                    if (state.gatheringStartMinutes <= state.gatheringEndMinutes) {
                        currentMinutes in state.gatheringStartMinutes until state.gatheringEndMinutes
                    } else {
                        currentMinutes >= state.gatheringStartMinutes || currentMinutes < state.gatheringEndMinutes
                    }

                if (isGathering && currentDay != lastNotifiedDay) {
                    lastNotifiedDay = currentDay

                    val unresolvedVaultItems = state.vaultItems.count { !it.isResolved && !it.isDeleted }

                    sendGatheringNotification(unresolvedVaultItems)
                }

                val currentDayOfWeek =
                    when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                        java.util.Calendar.MONDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY
                        java.util.Calendar.TUESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.TUESDAY
                        java.util.Calendar.WEDNESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.WEDNESDAY
                        java.util.Calendar.THURSDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.THURSDAY
                        java.util.Calendar.FRIDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.FRIDAY
                        java.util.Calendar.SATURDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.SATURDAY
                        else -> com.aegisgatekeeper.app.domain.DayOfWeek.SUNDAY
                    }

                val startOfDayCalendar =
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                val startOfDay = startOfDayCalendar.timeInMillis

                state.appGroups.forEach { group ->
                    group.rules.filterIsInstance<com.aegisgatekeeper.app.domain.BlockingRule.CheckIn>().forEach { rule ->
                        if (rule.isEnabled && rule.daysOfWeek.contains(currentDayOfWeek)) {
                            rule.checkInTimesMinutes.forEach { time ->
                                val checkInKey = "${group.id}_${time}_$currentDay"
                                if (currentMinutes >= time && !notifiedCheckIns.contains(checkInKey)) {
                                    val isConsumed =
                                        state.consumedCheckIns.any {
                                            it.groupId == group.id && it.timeMinutes == time &&
                                                it.timestamp >= startOfDay
                                        }
                                    if (!isConsumed) {
                                        val groupNotifications =
                                            state.notificationDigest.count { log ->
                                                group.apps.contains(log.packageName) &&
                                                    com.aegisgatekeeper.app.domain.isMailDelivered(
                                                        log.timestamp,
                                                        System.currentTimeMillis(),
                                                        rule,
                                                        currentDayOfWeek,
                                                    )
                                            }
                                        sendCheckInNotification(group.name, time, groupNotifications)
                                    }
                                    notifiedCheckIns.add(checkInKey)
                                }
                            }
                        }
                    }
                }

                notifiedCheckIns.removeAll { !it.endsWith("_$currentDay") }

                // Android NotificationListener Service Reliability Hack
                if (!GatekeeperNotificationListenerService.isConnected) {
                    if (com.aegisgatekeeper.app.services.PermissionChecker.hasNotificationAccessPermission(
                            this@GatekeeperForegroundService,
                        )
                    ) {
                        android.util.Log.w(
                            "Gatekeeper",
                            "🔧 NotificationListener disconnected despite permission. Toggling component to rebind.",
                        )
                        val pm = packageManager
                        val component =
                            android.content.ComponentName(
                                this@GatekeeperForegroundService,
                                GatekeeperNotificationListenerService::class.java,
                            )
                        pm.setComponentEnabledSetting(
                            component,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP,
                        )
                        pm.setComponentEnabledSetting(
                            component,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP,
                        )
                    }
                }

                delay(60_000L)
            }
        }
    }

    private fun sendCheckInNotification(
        groupName: String,
        time: Int,
        deliveredMailCount: Int = 0,
    ) {
        val channelId = "gatekeeper_phase_channel"
        val manager = getSystemService(NotificationManager::class.java)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    "Phase Transitions",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            manager.createNotificationChannel(channel)
        }

        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        val timeString = String.format("%02d:%02d", time / 60, time % 60)

        val text =
            if (deliveredMailCount > 0) {
                "A check-in token for $groupName is now available ($timeString). $deliveredMailCount messages delivered to the Digest."
            } else {
                "A check-in token for $groupName is now available ($timeString)."
            }

        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Check-In Available")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(groupName.hashCode(), notification)
    }

    private fun sendGatheringNotification(vaultCount: Int) {
        val channelId = "gatekeeper_phase_channel"
        val manager = getSystemService(NotificationManager::class.java)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    "Phase Transitions",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            manager.createNotificationChannel(channel)
        }

        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        val text =
            buildString {
                if (vaultCount > 0) append("$vaultCount thoughts to process. ")
                if (isEmpty()) append("Time to review your digital intake.")
            }

        val notification =
            NotificationCompat
                .Builder(this, channelId)
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
                if (!com.aegisgatekeeper.app.App.isRunningTest) {
                    val state = GatekeeperStateManager.state.value

                    // Layer Alpha polls UsageStats if Accessibility is offline or fails
                    val currentApp =
                        if (!state.isLayerOmegaActive) {
                            ForegroundAppDetector.getForegroundApp(this@GatekeeperForegroundService) ?: state.activeForegroundApp
                        } else {
                            state.activeForegroundApp
                        }

                                        if (currentApp != null) {
                        com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(this@GatekeeperForegroundService, currentApp)
                    }
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
