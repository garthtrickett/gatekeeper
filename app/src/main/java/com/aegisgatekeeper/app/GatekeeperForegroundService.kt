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
        startForeground(1, notification)

        // 4. Start the Layer Alpha Heartbeat (Polling)
        startLayerAlphaHeartbeat()

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
                    performAppValidation(this@GatekeeperForegroundService, currentApp)
                }

                // snappier polling (500ms) to reduce the "flash" on the free tier
                delay(500L)
            }
        }
    }

    companion object {
        private var lastDetectedPackage: String? = null
        private var ticksSinceLastUsageCheck = 0
        private val cachedUsageMinutes = mutableMapOf<String, Int>()

        /**
         * Centralized validation logic called by both the Foreground Heartbeat (Alpha)
         * and the Accessibility Event Stream (Omega) for zero-latency blocking.
         */
        fun performAppValidation(
            context: Context,
            currentApp: String,
        ) {
            val state = GatekeeperStateManager.state.value
            val activeGroups = state.appGroups.filter { it.apps.contains(currentApp) }

            Log.d("Gatekeeper", "👁️ Validating app: $currentApp | Active Groups Found: ${activeGroups.size}")

            if (activeGroups.isNotEmpty()) {
                val calendar = Calendar.getInstance()
                val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                val currentDay =
                    when (calendar.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY
                        Calendar.TUESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.TUESDAY
                        Calendar.WEDNESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.WEDNESDAY
                        Calendar.THURSDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.THURSDAY
                        Calendar.FRIDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.FRIDAY
                        Calendar.SATURDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.SATURDAY
                        else -> com.aegisgatekeeper.app.domain.DayOfWeek.SUNDAY
                    }

                var isBlocked = false
                var blockReason = ""

                // UsageStats queries are expensive; we only check time limits every ~2.5 seconds
                // CRITICAL: Always check immediately if the user just switched to a new app to prevent the "flash"
                val isNewApp = currentApp != lastDetectedPackage
                val checkUsage = isNewApp || ticksSinceLastUsageCheck >= 5
                if (checkUsage) ticksSinceLastUsageCheck = 0

                if (state.isManualLockdownActive) {
                    isBlocked = true
                    blockReason = "Manual Lockdown Engaged"
                } else {
                    for (group in activeGroups) {
                        val groupViolations = mutableListOf<String>()
                        val enabledRules = group.rules.filter { it.isEnabled }

                        if (enabledRules.isEmpty()) continue

                        for (rule in enabledRules) {
                            when (rule) {
                                is com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock -> {
                                    if (rule.daysOfWeek.contains(currentDay)) {
                                        val isAnySlotActive =
                                            rule.timeSlots.any {
                                                currentMinutes in it.startTimeMinutes..it.endTimeMinutes
                                            }
                                        if (isAnySlotActive) {
                                            groupViolations.add("Scheduled Block")
                                        }
                                    }
                                }

                                is com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit -> {
                                    val usageMinutes =
                                        if (checkUsage) {
                                            val usage = getDailyUsageMinutes(context, group.apps)
                                            cachedUsageMinutes[group.id] = usage
                                            usage
                                        } else {
                                            cachedUsageMinutes[group.id] ?: 0
                                        }

                                    if (usageMinutes >= rule.timeLimitMinutes) {
                                        groupViolations.add("Time Limit (${rule.timeLimitMinutes}m)")
                                    }
                                }

                                is com.aegisgatekeeper.app.domain.BlockingRule.CheckIn -> {
                                    if (rule.daysOfWeek.contains(currentDay)) {
                                        groupViolations.add("Check-In Required")
                                    }
                                }

                                is com.aegisgatekeeper.app.domain.BlockingRule.DomainBlock -> {
                                    // VPN handles this, not foreground service blocker
                                }
                            }
                        }

                        val groupIsBlocked =
                            when (group.combinator) {
                                com.aegisgatekeeper.app.domain.RuleCombinator.ANY -> {
                                    groupViolations.isNotEmpty()
                                }

                                com.aegisgatekeeper.app.domain.RuleCombinator.ALL -> {
                                    groupViolations.size == enabledRules.size &&
                                        enabledRules.isNotEmpty()
                                }
                            }

                        if (groupIsBlocked) {
                            isBlocked = true
                            blockReason = "Policy Violation: " + groupViolations.joinToString(" AND ") + " for '${group.name}'"
                            break
                        }
                    }
                }

                if (isBlocked) {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.RuleViolationDetected(currentApp, blockReason, System.currentTimeMillis()),
                    )
                } else if (currentApp != lastDetectedPackage) {
                    GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
                }

                ticksSinceLastUsageCheck++
            } else if (currentApp != lastDetectedPackage) {
                GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
            }

            lastDetectedPackage = currentApp
        }

        private fun getDailyUsageMinutes(
            context: Context,
            packages: Set<String>,
        ): Int {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            var totalTime = 0L
            for (pkg in packages) {
                stats[pkg]?.let { totalTime += it.totalTimeInForeground }
            }
            return (totalTime / 60000).toInt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
