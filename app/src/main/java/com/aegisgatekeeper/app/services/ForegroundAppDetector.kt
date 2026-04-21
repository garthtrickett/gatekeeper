package com.aegisgatekeeper.app.services

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Layer Alpha Core Logic:
 * Polls the Android UsageStatsManager to find the most recently opened app.
 */
object ForegroundAppDetector {
    /**
     * Queries the OS for the most recently resumed activity within the last [windowMillis].
     * Returns the package name, or null if no recent foreground event was found.
     */
    fun getForegroundApp(
        context: Context,
        windowMillis: Long = 2000L,
    ): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - windowMillis

        val usageEvents =
            try {
                usageStatsManager.queryEvents(startTime, endTime)
            } catch (e: SecurityException) {
                // This is an expected failure if the user has not granted PACKAGE_USAGE_STATS.
                // We return null and the service will simply tick again.
                return null
            }
        val event = UsageEvents.Event()

        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            // Event type 1 is ACTIVITY_RESUMED (previously MOVE_TO_FOREGROUND)
            @Suppress("DEPRECATION")
            val isForegroundEvent =
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

            if (isForegroundEvent) {
                if (event.timeStamp > latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }

        return latestPackage
    }
}
