package com.aegisgatekeeper.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aegisgatekeeper.app.domain.GatekeeperAction

class GatekeeperNotificationListenerService : NotificationListenerService() {
    companion object {
        var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i("Gatekeeper", "✅ NotificationListenerService Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w("Gatekeeper", "❌ NotificationListenerService Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val notification = sbn.notification
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) {
            return // Safety Filter: Do not kill ongoing events or foreground services
        }

        val packageName = sbn.packageName

        if (isAppBlocked(packageName)) {
            Log.i("Gatekeeper", "🛡️ Intercepting notification from $packageName")
            cancelNotification(sbn.key)

            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "No Title"
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Content"

            GatekeeperStateManager.dispatch(
                GatekeeperAction.NotificationIntercepted(
                    packageName = packageName,
                    title = title,
                    content = text,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun isAppBlocked(packageName: String): Boolean {
        val state = GatekeeperStateManager.state.value

        // If there's a valid temporary whitelist, it's not blocked
        val whitelist = state.interception.activeWhitelists[packageName]
        if (whitelist != null && System.currentTimeMillis() < whitelist.expiresAtTimestamp) {
            return false
        }

        val activeGroups = state.interception.appGroups.filter { it.apps.contains(packageName) }
        if (activeGroups.isEmpty()) return false

        if (state.interception.isManualLockdownActive) return true

        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        val currentDay =
            when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY
                java.util.Calendar.TUESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.TUESDAY
                java.util.Calendar.WEDNESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.WEDNESDAY
                java.util.Calendar.THURSDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.THURSDAY
                java.util.Calendar.FRIDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.FRIDAY
                java.util.Calendar.SATURDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.SATURDAY
                else -> com.aegisgatekeeper.app.domain.DayOfWeek.SUNDAY
            }

        for (group in activeGroups) {
            val groupViolations = mutableListOf<String>()
            val enabledRules = group.rules.filter { it.isEnabled }

            if (enabledRules.isEmpty()) continue

            for (rule in enabledRules) {
                when (rule) {
                    is com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock -> {
                        if (rule.daysOfWeek.contains(currentDay)) {
                            val activeSlots = rule.timeSlots.filter { currentMinutes in it.startTimeMinutes..it.endTimeMinutes }
                            if (activeSlots.isNotEmpty()) groupViolations.add("Scheduled Block")
                        }
                    }

                    is com.aegisgatekeeper.app.domain.BlockingRule.CheckIn -> {
                        if (rule.daysOfWeek.contains(currentDay)) {
                            groupViolations.add("Check-In Required")
                        }
                    }

                    is com.aegisgatekeeper.app.domain.BlockingRule.AlwaysBlock -> {
                        groupViolations.add("Always Block")
                    }

                    else -> {}
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

            if (groupIsBlocked) return true
        }

        return false
    }
}
