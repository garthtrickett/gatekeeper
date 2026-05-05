package com.aegisgatekeeper.app.domain

fun evaluateRules(snapshot: RuleEvaluationSnapshot): EvaluationVerdict {
    if (snapshot.isManualLockdownActive) {
        return EvaluationVerdict.Blocked("Manual Lockdown Engaged")
    }

    for (group in snapshot.activeGroups) {
        val groupViolations = mutableListOf<String>()
        val enabledRules = group.rules.filter { it.isEnabled }

        if (enabledRules.isEmpty()) continue

        for (rule in enabledRules) {
            when (rule) {
                is BlockingRule.ScheduledBlock -> {
                    if (rule.daysOfWeek.contains(snapshot.currentDay)) {
                        val activeSlots = rule.timeSlots.filter { snapshot.currentMinutes in it.startTimeMinutes..it.endTimeMinutes }
                        if (activeSlots.isNotEmpty()) {
                            val slotsStr =
                                activeSlots.joinToString(", ") { slot ->
                                    val startH = (slot.startTimeMinutes / 60).toString().padStart(2, '0')
                                    val startM = (slot.startTimeMinutes % 60).toString().padStart(2, '0')
                                    val endH = (slot.endTimeMinutes / 60).toString().padStart(2, '0')
                                    val endM = (slot.endTimeMinutes % 60).toString().padStart(2, '0')
                                    "$startH:$startM - $endH:$endM"
                                }
                            groupViolations.add("Scheduled Block ($slotsStr)")
                        }
                    }
                }

                is BlockingRule.TimeLimit -> {
                    val usageMinutes = group.apps.sumOf { snapshot.usageStats[it] ?: 0 }
                    if (usageMinutes >= rule.timeLimitMinutes) {
                        val timeLeft = maxOf(0, rule.timeLimitMinutes - usageMinutes)
                        groupViolations.add("Time Limit Reached (${timeLeft}m left, used $usageMinutes/${rule.timeLimitMinutes}m)")
                    }
                }

                is BlockingRule.CheckIn -> {
                    if (rule.daysOfWeek.contains(snapshot.currentDay)) {
                        val timesStr =
                            rule.checkInTimesMinutes.sorted().joinToString(", ") { time ->
                                val h = (time / 60).toString().padStart(2, '0')
                                val m = (time % 60).toString().padStart(2, '0')
                                "$h:$m"
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
                RuleCombinator.ANY -> groupViolations.isNotEmpty()
                RuleCombinator.ALL -> groupViolations.size == enabledRules.size && enabledRules.isNotEmpty()
            }

        if (groupIsBlocked) {
            return EvaluationVerdict.Blocked(
                "Policy Violation: " + groupViolations.joinToString(" AND ") + " for '${group.name}'",
            )
        }
    }

    return EvaluationVerdict.Allowed
}
