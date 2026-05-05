package com.aegisgatekeeper.app.services

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.DayOfWeek
import com.aegisgatekeeper.app.domain.EvaluationVerdict
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.RuleEvaluationSnapshot
import com.aegisgatekeeper.app.domain.evaluateRules
import java.util.Calendar

object AndroidRuleEvaluator {
    private var lastDetectedPackage: String? = null
    private var ticksSinceLastUsageCheck = 0
    private val cachedUsageMinutes = mutableMapOf<String, Int>()

    fun performAppValidation(
        context: Context,
        currentApp: String,
    ) {
        if (currentApp == "com.android.systemui" ||
            currentApp.contains("inputmethod", ignoreCase = true) ||
            currentApp.contains("keyboard", ignoreCase = true)
        ) {
            return
        }

        val state = GatekeeperStateManager.state.value

        if (state.interception.isOverlayActive && currentApp == context.packageName) {
            return
        }

        val isNewApp = currentApp != lastDetectedPackage

        val activeGroups = state.interception.appGroups.filter { it.apps.contains(currentApp) }

        if (isNewApp) {
            Log.d("Gatekeeper", "\uD83D\uDC41\uFE0F Validating app: $currentApp | Active Groups Found: ${activeGroups.size}")
        }

        if (activeGroups.isNotEmpty() || state.interception.isManualLockdownActive) {
            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val currentDay =
                when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> DayOfWeek.MONDAY
                    Calendar.TUESDAY -> DayOfWeek.TUESDAY
                    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
                    Calendar.THURSDAY -> DayOfWeek.THURSDAY
                    Calendar.FRIDAY -> DayOfWeek.FRIDAY
                    Calendar.SATURDAY -> DayOfWeek.SATURDAY
                    else -> DayOfWeek.SUNDAY
                }

            val checkUsage = isNewApp || ticksSinceLastUsageCheck >= 5
            if (checkUsage) {
                ticksSinceLastUsageCheck = 0
                val packagesToCheck = state.interception.appGroups.flatMap { it.apps }.toSet()
                val usages = getDailyUsageMinutes(context, packagesToCheck)
                cachedUsageMinutes.putAll(usages)
            }

            val snapshot =
                RuleEvaluationSnapshot(
                    targetPackage = currentApp,
                    activeGroups = activeGroups,
                    isManualLockdownActive = state.interception.isManualLockdownActive,
                    currentMinutes = currentMinutes,
                    currentDay = currentDay,
                    usageStats = cachedUsageMinutes,
                )

            val whitelist = state.interception.activeWhitelists[currentApp]
            val isWhitelisted = whitelist != null && whitelist.expiresAtTimestamp > System.currentTimeMillis()

            if (isWhitelisted) {
                if (isNewApp) {
                    GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
                }
            } else {
                val verdict = evaluateRules(snapshot)
                when (verdict) {
                    is EvaluationVerdict.Blocked -> {
                        if (isNewApp || !state.interception.isOverlayActive) {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.RuleViolationDetected(currentApp, verdict.reason, System.currentTimeMillis()),
                            )
                        }
                    }
                    is EvaluationVerdict.Allowed -> {
                        if (isNewApp) {
                            GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
                        }
                    }
                }
            }
            ticksSinceLastUsageCheck++
        } else if (isNewApp) {
            GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
        }

        lastDetectedPackage = currentApp
    }
        context: Context,
        currentApp: String,
    ) {
        if (currentApp == "com.android.systemui" ||
            currentApp.contains("inputmethod", ignoreCase = true) ||
            currentApp.contains("keyboard", ignoreCase = true)
        ) {
            return
        }

        val state = GatekeeperStateManager.state.value

        if (state.interception.isOverlayActive && currentApp == context.packageName) {
            return
        }

        val isNewApp = currentApp != lastDetectedPackage

        val activeGroups = state.interception.appGroups.filter { it.apps.contains(currentApp) }

        Log.d("Gatekeeper", "👁️ Validating app: $currentApp | Active Groups Found: ${activeGroups.size}")

        if (activeGroups.isNotEmpty()) {
            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val currentDay =
                when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> DayOfWeek.MONDAY
                    Calendar.TUESDAY -> DayOfWeek.TUESDAY
                    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
                    Calendar.THURSDAY -> DayOfWeek.THURSDAY
                    Calendar.FRIDAY -> DayOfWeek.FRIDAY
                    Calendar.SATURDAY -> DayOfWeek.SATURDAY
                    else -> DayOfWeek.SUNDAY
                }

            val checkUsage = isNewApp || ticksSinceLastUsageCheck >= 5
            if (checkUsage) {
                ticksSinceLastUsageCheck = 0
                val packagesToCheck = activeGroups.flatMap { it.apps }.toSet()
                val usages = getDailyUsageMinutes(context, packagesToCheck)
                cachedUsageMinutes.putAll(usages)
            }

            val snapshot =
                RuleEvaluationSnapshot(
                    targetPackage = currentApp,
                    activeGroups = activeGroups,
                    isManualLockdownActive = state.interception.isManualLockdownActive,
                    currentMinutes = currentMinutes,
                    currentDay = currentDay,
                    usageStats = cachedUsageMinutes,
                )

            val verdict = evaluateRules(snapshot)

            when (verdict) {
                is EvaluationVerdict.Blocked -> {
                    if (isNewApp || !state.interception.isOverlayActive) {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.RuleViolationDetected(currentApp, verdict.reason, System.currentTimeMillis()),
                        )
                    }
                }

                is EvaluationVerdict.Allowed -> {
                    if (isNewApp) {
                        GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
                    }
                }
            }

            ticksSinceLastUsageCheck++
        } else if (isNewApp) {
            GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
        }

        lastDetectedPackage = currentApp
    }

    private fun getDailyUsageMinutes(
        context: Context,
        packages: Set<String>,
    ): Map<String, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val result = mutableMapOf<String, Int>()
        for (pkg in packages) {
            val totalTime = stats[pkg]?.totalTimeInForeground ?: 0L
            result[pkg] = (totalTime / 60000).toInt()
        }
        return result
    }
}
