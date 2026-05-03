package com.aegisgatekeeper.app.services

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.DayOfWeek
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.RuleCombinator
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

        if (isNewApp) {
            if (lastDetectedPackage != null) {
                val whitelist = state.interception.activeWhitelists[lastDetectedPackage]
                if (whitelist != null && System.currentTimeMillis() < whitelist.expiresAtTimestamp &&
                    whitelist.reason != "GIVE_UP_GRACE_PERIOD"
                ) {
                    GatekeeperStateManager.dispatch(GatekeeperAction.TriggerExitInterview(lastDetectedPackage!!))
                }
            }
        }

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

            var isBlocked = false
            var blockReason = ""

            val checkUsage = isNewApp || ticksSinceLastUsageCheck >= 5
            if (checkUsage) ticksSinceLastUsageCheck = 0

            if (state.interception.isManualLockdownActive) {
                isBlocked = true
                blockReason = "Manual Lockdown Engaged"
            } else {
                for (group in activeGroups) {
                    val groupViolations = mutableListOf<String>()
                    val enabledRules = group.rules.filter { it.isEnabled }

                    if (enabledRules.isEmpty()) continue

                    for (rule in enabledRules) {
                        when (rule) {
                            is BlockingRule.ScheduledBlock -> {
                                if (rule.daysOfWeek.contains(currentDay)) {
                                    val activeSlots = rule.timeSlots.filter { currentMinutes in it.startTimeMinutes..it.endTimeMinutes }
                                    if (activeSlots.isNotEmpty()) {
                                        val slotsStr =
                                            activeSlots.joinToString(", ") {
                                                String.format(
                                                    "%02d:%02d - %02d:%02d",
                                                    it.startTimeMinutes / 60,
                                                    it.startTimeMinutes % 60,
                                                    it.endTimeMinutes / 60,
                                                    it.endTimeMinutes % 60,
                                                )
                                            }
                                        groupViolations.add("Scheduled Block ($slotsStr)")
                                    }
                                }
                            }

                            is BlockingRule.TimeLimit -> {
                                val usageMinutes =
                                    if (checkUsage) {
                                        val usage = getDailyUsageMinutes(context, group.apps)
                                        cachedUsageMinutes[group.id] = usage
                                        usage
                                    } else {
                                        cachedUsageMinutes[group.id] ?: 0
                                    }
                                if (usageMinutes >= rule.timeLimitMinutes) {
                                    val timeLeft = maxOf(0, rule.timeLimitMinutes - usageMinutes)
                                    groupViolations.add(
                                        "Time Limit Reached (${timeLeft}m left, used $usageMinutes/${rule.timeLimitMinutes}m)",
                                    )
                                }
                            }

                            is BlockingRule.CheckIn -> {
                                if (rule.daysOfWeek.contains(currentDay)) {
                                    val timesStr =
                                        rule.checkInTimesMinutes.sorted().joinToString(", ") {
                                            String.format("%02d:%02d", it / 60, it % 60)
                                        }
                                    groupViolations.add("Check-In Required ($timesStr)")
                                }
                            }

                            is BlockingRule.DomainBlock -> {}

                            is BlockingRule.AlwaysBlock -> {
                                groupViolations.add("Always Block")
                            }
                        }
                    }

                    val groupIsBlocked =
                        when (group.combinator) {
                            RuleCombinator.ANY -> {
                                groupViolations.isNotEmpty()
                            }

                            RuleCombinator.ALL -> {
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
                if (isNewApp || !state.interception.isOverlayActive) {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.RuleViolationDetected(currentApp, blockReason, System.currentTimeMillis()),
                    )
                }
            } else if (isNewApp) {
                GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
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
    ): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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
