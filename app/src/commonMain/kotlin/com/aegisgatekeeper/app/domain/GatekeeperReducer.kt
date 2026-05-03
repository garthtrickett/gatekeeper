package com.aegisgatekeeper.app.domain

fun reduce(
    state: GatekeeperState,
    action: GatekeeperAction,
): Update<GatekeeperState> {
    if (action is GatekeeperAction.InitialStateLoaded) return Update(action.state)

    val (interception, interceptionEffects) = reduceInterception(state.interception, action, state)
    val (data, dataEffects) = reduceData(state.data, action, state)
    val (media, mediaEffects) = reduceMedia(state.media, action, state)
    val (sync, syncEffects) = reduceSyncAndIntegration(state.sync, action, state)

    var effects = interceptionEffects + dataEffects + mediaEffects + syncEffects

    if (action is GatekeeperAction.LoadInitialState) {
        effects = effects + GatekeeperEffect.DbLoadInitialState
    }

    return Update(
        state = state.copy(
            interception = interception,
            data = data,
            media = media,
            sync = sync,
        ),
                effects = effects
    )
}

    

private fun reduceInterception(
    slice: InterceptionState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<InterceptionState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState = when (action) {
        is GatekeeperAction.RuleViolationDetected -> {
            val whitelist = slice.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            val newSlice =
                slice.copy(
                    activeForegroundApp = action.packageName,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )

            if (!isWhitelistValid) {
                val wasExpired = whitelist != null
                newSlice.copy(
                    isOverlayActive = true,
                    currentlyInterceptedApp = action.packageName,
                    expiredSessionDurationMillis = if (wasExpired) whitelist.allocatedDurationMillis else null,
                    activeBlockReason = action.reason,
                    pendingExitInterview = null,
                )
            } else {
                newSlice
            }
        }

        is GatekeeperAction.AppBroughtToForeground -> {
            val whitelist = slice.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            if (slice.pendingExitInterview != null) {
                slice.copy(
                    activeForegroundApp = action.packageName,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )
            } else {
                slice.copy(
                    activeForegroundApp = action.packageName,
                    isOverlayActive = false,
                    currentlyInterceptedApp = null,
                    expiredSessionDurationMillis = null,
                    activeBlockReason = null,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )
            }
        }

        GatekeeperAction.DismissOverlay -> {
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
            )
        }

        is GatekeeperAction.SessionExpired -> {
            slice.copy(
                isOverlayActive = true,
                currentlyInterceptedApp = action.packageName,
                expiredSessionDurationMillis = action.allocatedDurationMillis,
                activeWhitelists = slice.activeWhitelists - action.packageName,
            )
        }

        GatekeeperAction.LayerOmegaConnected -> {
            slice.copy(isLayerOmegaActive = true)
        }

        GatekeeperAction.LayerOmegaDisconnected -> {
            slice.copy(isLayerOmegaActive = false)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            val expiresAt = action.currentTimestamp + action.allocatedDurationMillis
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = action.reason,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                    allocatedDurationMillis = action.allocatedDurationMillis,
                )
            effects.add(GatekeeperEffect.DbLogEmergencyBypass(action.packageName, action.reason, action.currentTimestamp))
            effects.add(GatekeeperEffect.LaunchAppAndStartSession(action.packageName, action.allocatedDurationMillis))
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.LogGiveUp -> {
            val gracePeriodExpires = action.currentTimestamp + 2000L
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = "GIVE_UP_GRACE_PERIOD",
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = gracePeriodExpires,
                    allocatedDurationMillis = 2000L,
                )
            effects.add(GatekeeperEffect.DbLogGiveUp(action.packageName, action.currentTimestamp))
            effects.add(GatekeeperEffect.GoHome)
            slice.copy(
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
            )
        }

        is GatekeeperAction.FrictionCompleted -> {
            val expiresAt = action.currentTimestamp + action.allocatedDurationMillis
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = null,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                    allocatedDurationMillis = action.allocatedDurationMillis,
                )
            effects.add(GatekeeperEffect.LaunchAppAndStartSession(action.packageName, action.allocatedDurationMillis))
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.WhitelistExpired -> {
            slice.copy(activeWhitelists = slice.activeWhitelists - action.packageName)
        }

        is GatekeeperAction.CreateAppGroup -> {
            effects.add(GatekeeperEffect.DbCreateAppGroup(action.id, action.name, action.apps, action.combinator))
            val newGroup = AppGroup(id = action.id, name = action.name, apps = action.apps, combinator = action.combinator)
            slice.copy(appGroups = slice.appGroups + newGroup)
        }

        is GatekeeperAction.UpdateGroupCombinator -> {
            effects.add(GatekeeperEffect.DbUpdateGroupCombinator(action.groupId, action.combinator))
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(combinator = action.combinator) else it })
        }

        is GatekeeperAction.UpdateGroupName -> {
            effects.add(GatekeeperEffect.DbUpdateGroupName(action.groupId, action.newName))
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(name = action.newName) else it })
        }

        is GatekeeperAction.UpdateGroupApps -> {
            effects.add(GatekeeperEffect.DbUpdateGroupApps(action.groupId, action.apps))
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(apps = action.apps) else it })
        }

        is GatekeeperAction.DeleteAppGroup -> {
            effects.add(GatekeeperEffect.DbDeleteAppGroup(action.groupId))
            slice.copy(appGroups = slice.appGroups.filter { it.id != action.groupId })
        }

        is GatekeeperAction.AddAlwaysBlockRule -> {
            effects.add(GatekeeperEffect.DbAddAlwaysBlockRule(action.id, action.groupId))
            val newRule = BlockingRule.AlwaysBlock(id = action.id, groupId = action.groupId)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.AddDomainBlockRule -> {
            effects.add(GatekeeperEffect.DbAddDomainBlockRule(action.id, action.groupId, action.domains))
            val newRule = BlockingRule.DomainBlock(id = action.id, groupId = action.groupId, domains = action.domains)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.UpdateDomainBlockRule -> {
            effects.add(GatekeeperEffect.DbUpdateDomainBlockRule(action.ruleId, action.domains))
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { rule ->
                                        if (rule.id == action.ruleId &&
                                            rule is BlockingRule.DomainBlock
                                        ) {
                                            rule.copy(domains = action.domains)
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.AddTimeLimitRule -> {
            effects.add(GatekeeperEffect.DbAddTimeLimitRule(action.id, action.groupId, action.timeLimitMinutes))
            val newRule = BlockingRule.TimeLimit(id = action.id, groupId = action.groupId, timeLimitMinutes = action.timeLimitMinutes)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.AddScheduledBlockRule -> {
            effects.add(GatekeeperEffect.DbAddScheduledBlockRule(action.id, action.groupId, action.timeSlots, action.daysOfWeek))
            val newRule =
                BlockingRule.ScheduledBlock(
                    id = action.id,
                    groupId = action.groupId,
                    timeSlots = action.timeSlots,
                    daysOfWeek = action.daysOfWeek,
                )
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.DeleteRule -> {
            effects.add(GatekeeperEffect.DbDeleteRule(action.ruleId))
            slice.copy(
                appGroups =
                    slice.appGroups.map {
                        if (it.id ==
                            action.groupId
                        ) {
                            it.copy(rules = it.rules.filter { r -> r.id != action.ruleId })
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.ToggleRule -> {
            effects.add(GatekeeperEffect.DbToggleRule(action.ruleId, action.isEnabled))
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { r ->
                                        if (r.id == action.ruleId) {
                                            when (r) {
                                                is BlockingRule.TimeLimit -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.ScheduledBlock -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.CheckIn -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.DomainBlock -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.AlwaysBlock -> r.copy(isEnabled = action.isEnabled)
                                            }
                                        } else {
                                            r
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.AddCheckInRule -> {
            effects.add(GatekeeperEffect.DbAddCheckInRule(action.id, action.groupId, action.checkInTimesMinutes, action.durationMinutes, action.daysOfWeek))
            val newRule =
                BlockingRule.CheckIn(
                    id = action.id,
                    groupId = action.groupId,
                    checkInTimesMinutes = action.checkInTimesMinutes,
                    durationMinutes = action.durationMinutes,
                    daysOfWeek = action.daysOfWeek,
                )
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.UpdateCheckInRule -> {
            effects.add(GatekeeperEffect.DbUpdateCheckInRule(action.id, action.checkInTimesMinutes, action.durationMinutes, action.daysOfWeek))
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { rule ->
                                        if (rule.id == action.id && rule is BlockingRule.CheckIn) {
                                            rule.copy(
                                                checkInTimesMinutes = action.checkInTimesMinutes,
                                                durationMinutes = action.durationMinutes,
                                                daysOfWeek = action.daysOfWeek,
                                            )
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val group = slice.appGroups.find { it.id == action.groupId }
            val whitelists =
                group?.apps?.associate { app ->
                    app to
                        TemporaryWhitelist(
                            packageName = app,
                            reason = action.reason ?: "Check-In Token",
                            grantedAtTimestamp = action.currentTimestamp,
                            expiresAtTimestamp = action.currentTimestamp + (action.durationMinutes * 60_000L),
                            allocatedDurationMillis = action.durationMinutes * 60_000L,
                        )
                } ?: emptyMap()

            val dismissed = slice.currentlyInterceptedApp in (group?.apps ?: emptySet())
            
            val newLog = ConsumedCheckIn(groupId = action.groupId, timeMinutes = action.checkInTimeMinutes, timestamp = action.currentTimestamp)
            effects.add(GatekeeperEffect.DbRedeemCheckInToken(newLog, action.groupId, action.reason, action.currentTimestamp))
            
            if (dismissed) {
                effects.add(GatekeeperEffect.LaunchAppAndStartSession(slice.currentlyInterceptedApp!!, action.durationMinutes * 60_000L))
            }

            slice.copy(
                isOverlayActive = if (dismissed) false else slice.isOverlayActive,
                currentlyInterceptedApp = if (dismissed) null else slice.currentlyInterceptedApp,
                expiredSessionDurationMillis = if (dismissed) null else slice.expiredSessionDurationMillis,
                activeBlockReason = if (dismissed) null else slice.activeBlockReason,
                pendingExitInterview = if (dismissed) null else slice.pendingExitInterview,
                activeWhitelists = slice.activeWhitelists + whitelists,
            )
        }

        is GatekeeperAction.EndGroupSession -> {
            val group = slice.appGroups.find { it.id == action.groupId }
            val appsToRemove = group?.apps ?: emptySet()
            slice.copy(activeWhitelists = slice.activeWhitelists.filterKeys { it !in appsToRemove })
        }

        is GatekeeperAction.EndAppSession -> {
            slice.copy(
                activeWhitelists = slice.activeWhitelists - action.packageName,
                isOverlayActive = if (slice.pendingExitInterview == action.packageName) false else slice.isOverlayActive,
                pendingExitInterview = if (slice.pendingExitInterview == action.packageName) null else slice.pendingExitInterview,
            )
        }

        is GatekeeperAction.TriggerExitInterview -> {
            slice.copy(isOverlayActive = true, pendingExitInterview = action.packageName)
        }

        GatekeeperAction.CancelExitInterview -> {
            slice.copy(isOverlayActive = false, pendingExitInterview = null)
        }

        is GatekeeperAction.SetFrictionGame -> {
            effects.add(GatekeeperEffect.DbSetFrictionGame(action.game))
            slice.copy(activeFrictionGame = action.game)
        }

        is GatekeeperAction.SetManualLockdown -> {
            effects.add(GatekeeperEffect.DbSetManualLockdown(action.isActive))
            slice.copy(isManualLockdownActive = action.isActive)
        }

        is GatekeeperAction.PermissionsUpdated -> {
            slice.copy(
                hasOverlayPermission = action.hasOverlay,
                hasUsageAccessPermission = action.hasUsageAccess,
                hasAccessibilityPermission = action.hasAccessibility,
                hasNotificationAccessPermission = action.hasNotificationAccess,
                isBatteryOptimizationDisabled = action.isBatteryDisabled,
            )
        }

        GatekeeperAction.ReengageShields -> {
            slice.copy(activeWhitelists = emptyMap())
        }
        
        is GatekeeperAction.ResetCheckIns -> {
            effects.add(GatekeeperEffect.DbResetCheckIns(action.groupId))
            slice
        }

        else -> {
            slice
        }
    }
                return Update(newState, effects)
}

fun deletedReduce2(
    slice: InterceptionState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): InterceptionState =
    when (action) {
        is GatekeeperAction.RuleViolationDetected -> {
            val whitelist = slice.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            val newSlice =
                slice.copy(
                    activeForegroundApp = action.packageName,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )

            if (!isWhitelistValid) {
                val wasExpired = whitelist != null
                newSlice.copy(
                    isOverlayActive = true,
                    currentlyInterceptedApp = action.packageName,
                    expiredSessionDurationMillis = if (wasExpired) whitelist.allocatedDurationMillis else null,
                    activeBlockReason = action.reason,
                    pendingExitInterview = null,
                )
            } else {
                newSlice
            }
        }

        is GatekeeperAction.AppBroughtToForeground -> {
            val whitelist = slice.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            if (slice.pendingExitInterview != null) {
                slice.copy(
                    activeForegroundApp = action.packageName,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )
            } else {
                slice.copy(
                    activeForegroundApp = action.packageName,
                    isOverlayActive = false,
                    currentlyInterceptedApp = null,
                    expiredSessionDurationMillis = null,
                    activeBlockReason = null,
                    activeWhitelists =
                        if (whitelist != null && !isWhitelistValid) {
                            slice.activeWhitelists - action.packageName
                        } else {
                            slice.activeWhitelists
                        },
                )
            }
        }

        GatekeeperAction.DismissOverlay -> {
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
            )
        }

        is GatekeeperAction.SessionExpired -> {
            slice.copy(
                isOverlayActive = true,
                currentlyInterceptedApp = action.packageName,
                expiredSessionDurationMillis = action.allocatedDurationMillis,
                activeWhitelists = slice.activeWhitelists - action.packageName,
            )
        }

        GatekeeperAction.LayerOmegaConnected -> {
            slice.copy(isLayerOmegaActive = true)
        }

        GatekeeperAction.LayerOmegaDisconnected -> {
            slice.copy(isLayerOmegaActive = false)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            val expiresAt = action.currentTimestamp + action.allocatedDurationMillis
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = action.reason,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                    allocatedDurationMillis = action.allocatedDurationMillis,
                )
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.LogGiveUp -> {
            val gracePeriodExpires = action.currentTimestamp + 2000L
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = "GIVE_UP_GRACE_PERIOD",
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = gracePeriodExpires,
                    allocatedDurationMillis = 2000L,
                )
            slice.copy(
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
            )
        }

        is GatekeeperAction.FrictionCompleted -> {
            val expiresAt = action.currentTimestamp + action.allocatedDurationMillis
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = null,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                    allocatedDurationMillis = action.allocatedDurationMillis,
                )
            slice.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
                pendingExitInterview = null,
                activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.WhitelistExpired -> {
            slice.copy(activeWhitelists = slice.activeWhitelists - action.packageName)
        }

        is GatekeeperAction.CreateAppGroup -> {
            val newGroup = AppGroup(id = action.id, name = action.name, apps = action.apps, combinator = action.combinator)
            slice.copy(appGroups = slice.appGroups + newGroup)
        }

        is GatekeeperAction.UpdateGroupCombinator -> {
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(combinator = action.combinator) else it })
        }

        is GatekeeperAction.UpdateGroupName -> {
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(name = action.newName) else it })
        }

        is GatekeeperAction.UpdateGroupApps -> {
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(apps = action.apps) else it })
        }

        is GatekeeperAction.DeleteAppGroup -> {
            slice.copy(appGroups = slice.appGroups.filter { it.id != action.groupId })
        }

        is GatekeeperAction.AddAlwaysBlockRule -> {
            val newRule = BlockingRule.AlwaysBlock(id = action.id, groupId = action.groupId)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.AddDomainBlockRule -> {
            val newRule = BlockingRule.DomainBlock(id = action.id, groupId = action.groupId, domains = action.domains)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.UpdateDomainBlockRule -> {
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { rule ->
                                        if (rule.id == action.ruleId &&
                                            rule is BlockingRule.DomainBlock
                                        ) {
                                            rule.copy(domains = action.domains)
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.AddTimeLimitRule -> {
            val newRule = BlockingRule.TimeLimit(id = action.id, groupId = action.groupId, timeLimitMinutes = action.timeLimitMinutes)
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.AddScheduledBlockRule -> {
            val newRule =
                BlockingRule.ScheduledBlock(
                    id = action.id,
                    groupId = action.groupId,
                    timeSlots = action.timeSlots,
                    daysOfWeek = action.daysOfWeek,
                )
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.DeleteRule -> {
            slice.copy(
                appGroups =
                    slice.appGroups.map {
                        if (it.id ==
                            action.groupId
                        ) {
                            it.copy(rules = it.rules.filter { r -> r.id != action.ruleId })
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.ToggleRule -> {
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { r ->
                                        if (r.id == action.ruleId) {
                                            when (r) {
                                                is BlockingRule.TimeLimit -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.ScheduledBlock -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.CheckIn -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.DomainBlock -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.AlwaysBlock -> r.copy(isEnabled = action.isEnabled)
                                            }
                                        } else {
                                            r
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.AddCheckInRule -> {
            val newRule =
                BlockingRule.CheckIn(
                    id = action.id,
                    groupId = action.groupId,
                    checkInTimesMinutes = action.checkInTimesMinutes,
                    durationMinutes = action.durationMinutes,
                    daysOfWeek = action.daysOfWeek,
                )
            slice.copy(appGroups = slice.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.UpdateCheckInRule -> {
            slice.copy(
                appGroups =
                    slice.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { rule ->
                                        if (rule.id == action.id && rule is BlockingRule.CheckIn) {
                                            rule.copy(
                                                checkInTimesMinutes = action.checkInTimesMinutes,
                                                durationMinutes = action.durationMinutes,
                                                daysOfWeek = action.daysOfWeek,
                                            )
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        } else {
                            group
                        }
                    },
            )
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val group = slice.appGroups.find { it.id == action.groupId }
            val whitelists =
                group?.apps?.associate { app ->
                    app to
                        TemporaryWhitelist(
                            packageName = app,
                            reason = action.reason ?: "Check-In Token",
                            grantedAtTimestamp = action.currentTimestamp,
                            expiresAtTimestamp = action.currentTimestamp + (action.durationMinutes * 60_000L),
                            allocatedDurationMillis = action.durationMinutes * 60_000L,
                        )
                } ?: emptyMap()

            val dismissed = slice.currentlyInterceptedApp in (group?.apps ?: emptySet())

            slice.copy(
                isOverlayActive = if (dismissed) false else slice.isOverlayActive,
                currentlyInterceptedApp = if (dismissed) null else slice.currentlyInterceptedApp,
                expiredSessionDurationMillis = if (dismissed) null else slice.expiredSessionDurationMillis,
                activeBlockReason = if (dismissed) null else slice.activeBlockReason,
                pendingExitInterview = if (dismissed) null else slice.pendingExitInterview,
                activeWhitelists = slice.activeWhitelists + whitelists,
            )
        }

        is GatekeeperAction.EndGroupSession -> {
            val group = slice.appGroups.find { it.id == action.groupId }
            val appsToRemove = group?.apps ?: emptySet()
            slice.copy(activeWhitelists = slice.activeWhitelists.filterKeys { it !in appsToRemove })
        }

        is GatekeeperAction.EndAppSession -> {
            slice.copy(
                activeWhitelists = slice.activeWhitelists - action.packageName,
                isOverlayActive = if (slice.pendingExitInterview == action.packageName) false else slice.isOverlayActive,
                pendingExitInterview = if (slice.pendingExitInterview == action.packageName) null else slice.pendingExitInterview,
            )
        }

        is GatekeeperAction.TriggerExitInterview -> {
            slice.copy(isOverlayActive = true, pendingExitInterview = action.packageName)
        }

        GatekeeperAction.CancelExitInterview -> {
            slice.copy(isOverlayActive = false, pendingExitInterview = null)
        }

        is GatekeeperAction.SetFrictionGame -> {
            slice.copy(activeFrictionGame = action.game)
        }

        is GatekeeperAction.SetManualLockdown -> {
            slice.copy(isManualLockdownActive = action.isActive)
        }

        is GatekeeperAction.PermissionsUpdated -> {
            slice.copy(
                hasOverlayPermission = action.hasOverlay,
                hasUsageAccessPermission = action.hasUsageAccess,
                hasAccessibilityPermission = action.hasAccessibility,
                hasNotificationAccessPermission = action.hasNotificationAccess,
                isBatteryOptimizationDisabled = action.isBatteryDisabled,
            )
        }

        GatekeeperAction.ReengageShields -> {
            slice.copy(activeWhitelists = emptyMap())
        }

        else -> {
            slice
        }
    }

private fun reduceData(
    slice: DataState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<DataState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState = when (action) {
        is GatekeeperAction.SaveToVault -> {
            val newItem =
                VaultItem(query = action.query, capturedAtTimestamp = action.currentTimestamp, lastModified = action.currentTimestamp)
            effects.add(GatekeeperEffect.DbInsertVaultItem(newItem))
            slice.copy(vaultItems = slice.vaultItems + newItem)
        }

        is GatekeeperAction.MarkVaultItemResolved -> {
            effects.add(GatekeeperEffect.DbMarkVaultItemResolved(action.id, action.currentTimestamp))
            slice.copy(
                vaultItems =
                    slice.vaultItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(isResolved = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.SaveToContentBank -> {
            val existing = slice.contentItems.find { it.videoId == action.videoId && it.source == action.source }
            if (existing != null) {
                val updatedItem =
                    existing.copy(
                        title = action.title,
                        channelName = action.channelName ?: existing.channelName,
                        durationSeconds = action.durationSeconds ?: existing.durationSeconds,
                        podcastId = action.podcastId ?: existing.podcastId,
                        lastModified = action.currentTimestamp,
                        isDeleted = false,
                    )
                effects.add(GatekeeperEffect.DbUpsertContentItem(updatedItem))
                effects.add(GatekeeperEffect.TriggerWidgetUpdate)
                slice.copy(
                    contentItems = slice.contentItems.map { if (it.id == existing.id) updatedItem else it },
                    intentionalSlots =
                        slice.intentionalSlots.map { slot ->
                            if (slot.contentItem.id ==
                                existing.id
                            ) {
                                slot.copy(contentItem = updatedItem)
                            } else {
                                slot
                            }
                        },
                )
            } else {
                val newItem =
                    ContentItem(
                        videoId = action.videoId,
                        title = action.title,
                        channelName = action.channelName,
                        source = action.source,
                        type = action.type,
                        rank = slice.contentItems.size.toLong(),
                        capturedAtTimestamp = action.currentTimestamp,
                        durationSeconds = action.durationSeconds,
                        podcastId = action.podcastId,
                        lastModified = action.currentTimestamp,
                    )
                effects.add(GatekeeperEffect.DbUpsertContentItem(newItem))
                effects.add(GatekeeperEffect.TriggerWidgetUpdate)
                slice.copy(contentItems = slice.contentItems + newItem)
            }
        }

        is GatekeeperAction.ReorderContentBank -> {
            if (action.fromIndex !in slice.contentItems.indices || action.toIndex !in slice.contentItems.indices) {
                slice
            } else {
                val mutableList = slice.contentItems.toMutableList()
                val itemToMove = mutableList.removeAt(action.fromIndex)
                mutableList.add(action.toIndex, itemToMove)
                val updatedList =
                    mutableList.mapIndexed {
                        index,
                        item,
                        ->
                        val newItem = item.copy(rank = index.toLong(), lastModified = action.currentTimestamp)
                        effects.add(GatekeeperEffect.DbUpdateRank(newItem.id, newItem.rank, action.currentTimestamp))
                        newItem
                    }
                effects.add(GatekeeperEffect.TriggerWidgetUpdate)
                slice.copy(contentItems = updatedList)
            }
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            effects.add(GatekeeperEffect.DbRemoveFromContentBank(action.id, action.currentTimestamp))
            effects.add(GatekeeperEffect.TriggerWidgetUpdate)
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(isDeleted = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
                intentionalSlots = slice.intentionalSlots.filter { it.contentItem.id != action.id },
            )
        }

        is GatekeeperAction.SaveIntentionalSlot -> {
            effects.add(GatekeeperEffect.DbInsertIntentionalSlot(action.slotIndex, action.contentItem.id))
            effects.add(GatekeeperEffect.TriggerWidgetUpdate)
            val newItem = IntentionalSlotItem(slotIndex = action.slotIndex, contentItem = action.contentItem)
            val newList = slice.intentionalSlots.filter { it.slotIndex != action.slotIndex } + newItem
            slice.copy(intentionalSlots = newList)
        }

        is GatekeeperAction.ClearIntentionalSlot -> {
            effects.add(GatekeeperEffect.DbClearIntentionalSlot(action.slotIndex))
            effects.add(GatekeeperEffect.TriggerWidgetUpdate)
            slice.copy(intentionalSlots = slice.intentionalSlots.filter { it.slotIndex != action.slotIndex })
        }

        is GatekeeperAction.DownloadMediaRequested -> {
            val item = slice.contentItems.find { it.id == action.id }
            if (item != null) {
                effects.add(GatekeeperEffect.DownloadMedia(action.id, item.videoId))
                effects.add(GatekeeperEffect.DbUpdateDownloadStatus(action.id, DownloadStatus.QUEUED, null, currentTimeMillis()))
            }
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.QUEUED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadProgressUpdated -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadCompleted -> {
            effects.add(GatekeeperEffect.DbUpdateDownloadStatus(action.id, DownloadStatus.COMPLETED, action.localFilePath, currentTimeMillis()))
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.COMPLETED, localFilePath = action.localFilePath)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadFailed -> {
            effects.add(GatekeeperEffect.DbUpdateDownloadStatus(action.id, DownloadStatus.FAILED, null, currentTimeMillis()))
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.FAILED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            effects.add(GatekeeperEffect.DeleteDownloadedMedia(action.id))
            effects.add(GatekeeperEffect.DbUpdateDownloadStatus(action.id, DownloadStatus.NONE, null, currentTimeMillis()))
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.NONE, localFilePath = null)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.RemovePodcastSubscription -> {
            effects.add(GatekeeperEffect.DbDeletePodcastSubscription(action.id, action.currentTimestamp))
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.podcastId ==
                            action.id
                        ) {
                            it.copy(isDeleted = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
                intentionalSlots =
                    slice.intentionalSlots.filter { slot ->
                        slice.contentItems.find { it.id == slot.contentItem.id }?.podcastId !=
                            action.id
                    },
            )
        }

        is GatekeeperAction.SetCustomInterceptionMessage -> {
            effects.add(GatekeeperEffect.DbSetCustomInterceptionMessage(action.packageName, action.message))
            slice.copy(
                customMessages =
                    slice.customMessages + (action.packageName to action.message),
            )
        }

        is GatekeeperAction.RemoveCustomInterceptionMessage -> {
            effects.add(GatekeeperEffect.DbRemoveCustomInterceptionMessage(action.packageName))
            slice.copy(customMessages = slice.customMessages - action.packageName)
        }

        is GatekeeperAction.ResetCheckIns -> {
            slice.copy(consumedCheckIns = slice.consumedCheckIns.filter { it.groupId != action.groupId })
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val newLog =
                ConsumedCheckIn(groupId = action.groupId, timeMinutes = action.checkInTimeMinutes, timestamp = action.currentTimestamp)
            slice.copy(consumedCheckIns = slice.consumedCheckIns + newLog)
        }

        is GatekeeperAction.UpdatePhaseWindows -> {
            effects.add(GatekeeperEffect.DbUpdatePhaseWindows(action.deepWorkStartMinutes, action.deepWorkEndMinutes, action.gatheringStartMinutes, action.gatheringEndMinutes))
            slice.copy(
                deepWorkStartMinutes = action.deepWorkStartMinutes,
                deepWorkEndMinutes = action.deepWorkEndMinutes,
                gatheringStartMinutes = action.gatheringStartMinutes,
                gatheringEndMinutes = action.gatheringEndMinutes,
            )
        }

        is GatekeeperAction.UpdateMissionControlApps -> {
            effects.add(GatekeeperEffect.DbUpdateMissionControlApps(action.packageNames))
            slice.copy(missionControlApps = action.packageNames)
        }

        is GatekeeperAction.AddPinnedWebsite -> {
            val newWebsite = PinnedWebsite(action.id, action.label, action.url)
            effects.add(GatekeeperEffect.DbAddPinnedWebsite(newWebsite, slice.missionControlWebsites.size.toLong()))
            slice.copy(missionControlWebsites = slice.missionControlWebsites + newWebsite)
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            effects.add(GatekeeperEffect.DbRemovePinnedWebsite(action.id))
            slice.copy(missionControlWebsites = slice.missionControlWebsites.filter { it.id != action.id })
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = AlternativeActivity(description = action.description, createdAtTimestamp = action.currentTimestamp)
            effects.add(GatekeeperEffect.DbAddAlternativeActivity(newActivity))
            slice.copy(alternativeActivities = slice.alternativeActivities + newActivity)
        }

        is GatekeeperAction.RemoveAlternativeActivity -> {
            effects.add(GatekeeperEffect.DbRemoveAlternativeActivity(action.id))
            slice.copy(
                alternativeActivities =
                    slice.alternativeActivities.filter {
                        it.id !=
                            action.id
                    },
            )
        }

        is GatekeeperAction.TriggerMetacognition -> {
            slice.copy(pendingMetacognition = MetacognitionRequest(action.packageName, action.durationMillis))
        }

        GatekeeperAction.ClearMetacognition -> {
            slice.copy(pendingMetacognition = null)
        }

        is GatekeeperAction.LogSessionMetacognition -> {
            val newLog =
                SessionLog(
                    packageName = action.packageName,
                    durationMillis = action.durationMillis,
                    emotion = action.emotion,
                    loggedAtTimestamp = action.currentTimestamp,
                )
            effects.add(GatekeeperEffect.DbInsertSessionLog(newLog))
            slice.copy(sessionLogs = slice.sessionLogs + newLog)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
        }

        is GatekeeperAction.FrictionCompleted -> {
            slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
        }

        is GatekeeperAction.LogGiveUp -> {
            slice.copy(analyticsGiveUps = slice.analyticsGiveUps + 1)
        }

        is GatekeeperAction.ExportDataGenerated -> {
            slice.copy(exportData = action.data)
        }

        GatekeeperAction.ClearExportData -> {
            slice.copy(exportData = null)
        }
        
        GatekeeperAction.GenerateExportData -> {
            effects.add(GatekeeperEffect.GenerateExportData)
            slice
        }

        is GatekeeperAction.RemoteSyncCompleted -> {
            effects.add(GatekeeperEffect.DbRemoteSyncCompleted(action.newVaultItems, action.newContentItems))
            val localVaultMap = slice.vaultItems.associateBy { it.id }
            val remoteVaultMap = action.newVaultItems.associateBy { it.id }
            val allVaultIds = localVaultMap.keys + remoteVaultMap.keys

            val mergedVaultItems =
                allVaultIds.mapNotNull { id ->
                    val local = localVaultMap[id]
                    val remote = remoteVaultMap[id]
                    when {
                        local != null && remote != null -> if (remote.lastModified > local.lastModified) remote else local
                        remote != null -> remote
                        else -> local
                    }
                }

            val localContentMap = slice.contentItems.associateBy { it.id }
            val remoteContentMap = action.newContentItems.associateBy { it.id }
            val allContentIds = localContentMap.keys + remoteContentMap.keys

            val mergedContentItems =
                allContentIds.mapNotNull { id ->
                    val local = localContentMap[id]
                    val remote = remoteContentMap[id]
                    when {
                        local != null && remote != null -> {
                            if (remote.lastModified >
                                local.lastModified
                            ) {
                                remote.copy(localFilePath = local.localFilePath, downloadStatus = local.downloadStatus)
                            } else {
                                local
                            }
                        }

                        remote != null -> {
                            remote
                        }

                        else -> {
                            local
                        }
                    }
                }

            val newContentMap = mergedContentItems.associateBy { it.id }
            val updatedSlots =
                slice.intentionalSlots.map { slot ->
                    val newContent = newContentMap[slot.contentItem.id]
                    if (newContent != null) slot.copy(contentItem = newContent) else slot
                }

            slice.copy(vaultItems = mergedVaultItems, contentItems = mergedContentItems, intentionalSlots = updatedSlots)
        }

        else -> {
            slice
        }
    }
                return Update(newState, effects)
}

fun deletedReduce3(
    slice: DataState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): DataState =
    when (action) {
        is GatekeeperAction.SaveToVault -> {
            val newItem =
                VaultItem(query = action.query, capturedAtTimestamp = action.currentTimestamp, lastModified = action.currentTimestamp)
            slice.copy(vaultItems = slice.vaultItems + newItem)
        }

        is GatekeeperAction.MarkVaultItemResolved -> {
            slice.copy(
                vaultItems =
                    slice.vaultItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(isResolved = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.SaveToContentBank -> {
            val existing = slice.contentItems.find { it.videoId == action.videoId && it.source == action.source }
            if (existing != null) {
                val updatedItem =
                    existing.copy(
                        title = action.title,
                        channelName = action.channelName ?: existing.channelName,
                        durationSeconds = action.durationSeconds ?: existing.durationSeconds,
                        podcastId = action.podcastId ?: existing.podcastId,
                        lastModified = action.currentTimestamp,
                        isDeleted = false,
                    )
                slice.copy(
                    contentItems = slice.contentItems.map { if (it.id == existing.id) updatedItem else it },
                    intentionalSlots =
                        slice.intentionalSlots.map { slot ->
                            if (slot.contentItem.id ==
                                existing.id
                            ) {
                                slot.copy(contentItem = updatedItem)
                            } else {
                                slot
                            }
                        },
                )
            } else {
                val newItem =
                    ContentItem(
                        videoId = action.videoId,
                        title = action.title,
                        channelName = action.channelName,
                        source = action.source,
                        type = action.type,
                        rank = slice.contentItems.size.toLong(),
                        capturedAtTimestamp = action.currentTimestamp,
                        durationSeconds = action.durationSeconds,
                        podcastId = action.podcastId,
                        lastModified = action.currentTimestamp,
                    )
                slice.copy(contentItems = slice.contentItems + newItem)
            }
        }

        is GatekeeperAction.ReorderContentBank -> {
            if (action.fromIndex !in slice.contentItems.indices || action.toIndex !in slice.contentItems.indices) {
                slice
            } else {
                val mutableList = slice.contentItems.toMutableList()
                val itemToMove = mutableList.removeAt(action.fromIndex)
                mutableList.add(action.toIndex, itemToMove)
                val updatedList =
                    mutableList.mapIndexed {
                        index,
                        item,
                        ->
                        item.copy(rank = index.toLong(), lastModified = action.currentTimestamp)
                    }
                slice.copy(contentItems = updatedList)
            }
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(isDeleted = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
                intentionalSlots = slice.intentionalSlots.filter { it.contentItem.id != action.id },
            )
        }

        is GatekeeperAction.SaveIntentionalSlot -> {
            val newItem = IntentionalSlotItem(slotIndex = action.slotIndex, contentItem = action.contentItem)
            val newList = slice.intentionalSlots.filter { it.slotIndex != action.slotIndex } + newItem
            slice.copy(intentionalSlots = newList)
        }

        is GatekeeperAction.ClearIntentionalSlot -> {
            slice.copy(intentionalSlots = slice.intentionalSlots.filter { it.slotIndex != action.slotIndex })
        }

        is GatekeeperAction.DownloadMediaRequested -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.QUEUED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadProgressUpdated -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.DOWNLOADING)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadCompleted -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.COMPLETED, localFilePath = action.localFilePath)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DownloadFailed -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.FAILED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(downloadStatus = DownloadStatus.NONE, localFilePath = null)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.RemovePodcastSubscription -> {
            slice.copy(
                contentItems =
                    slice.contentItems.map {
                        if (it.podcastId ==
                            action.id
                        ) {
                            it.copy(isDeleted = true, lastModified = action.currentTimestamp)
                        } else {
                            it
                        }
                    },
                intentionalSlots =
                    slice.intentionalSlots.filter { slot ->
                        slice.contentItems.find { it.id == slot.contentItem.id }?.podcastId !=
                            action.id
                    },
            )
        }

        is GatekeeperAction.SetCustomInterceptionMessage -> {
            slice.copy(
                customMessages =
                    slice.customMessages + (action.packageName to action.message),
            )
        }

        is GatekeeperAction.RemoveCustomInterceptionMessage -> {
            slice.copy(customMessages = slice.customMessages - action.packageName)
        }

        is GatekeeperAction.ResetCheckIns -> {
            slice.copy(consumedCheckIns = slice.consumedCheckIns.filter { it.groupId != action.groupId })
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val newLog =
                ConsumedCheckIn(groupId = action.groupId, timeMinutes = action.checkInTimeMinutes, timestamp = action.currentTimestamp)
            slice.copy(consumedCheckIns = slice.consumedCheckIns + newLog)
        }

        is GatekeeperAction.UpdatePhaseWindows -> {
            slice.copy(
                deepWorkStartMinutes = action.deepWorkStartMinutes,
                deepWorkEndMinutes = action.deepWorkEndMinutes,
                gatheringStartMinutes = action.gatheringStartMinutes,
                gatheringEndMinutes = action.gatheringEndMinutes,
            )
        }

        is GatekeeperAction.UpdateMissionControlApps -> {
            slice.copy(missionControlApps = action.packageNames)
        }

        is GatekeeperAction.AddPinnedWebsite -> {
            val newWebsite = PinnedWebsite(action.id, action.label, action.url)
            slice.copy(missionControlWebsites = slice.missionControlWebsites + newWebsite)
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            slice.copy(missionControlWebsites = slice.missionControlWebsites.filter { it.id != action.id })
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = AlternativeActivity(description = action.description, createdAtTimestamp = action.currentTimestamp)
            slice.copy(alternativeActivities = slice.alternativeActivities + newActivity)
        }

        is GatekeeperAction.RemoveAlternativeActivity -> {
            slice.copy(
                alternativeActivities =
                    slice.alternativeActivities.filter {
                        it.id !=
                            action.id
                    },
            )
        }

        is GatekeeperAction.TriggerMetacognition -> {
            slice.copy(pendingMetacognition = MetacognitionRequest(action.packageName, action.durationMillis))
        }

        GatekeeperAction.ClearMetacognition -> {
            slice.copy(pendingMetacognition = null)
        }

        is GatekeeperAction.LogSessionMetacognition -> {
            val newLog =
                SessionLog(
                    packageName = action.packageName,
                    durationMillis = action.durationMillis,
                    emotion = action.emotion,
                    loggedAtTimestamp = action.currentTimestamp,
                )
            slice.copy(sessionLogs = slice.sessionLogs + newLog)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
        }

        is GatekeeperAction.FrictionCompleted -> {
            slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
        }

        is GatekeeperAction.LogGiveUp -> {
            slice.copy(analyticsGiveUps = slice.analyticsGiveUps + 1)
        }

        is GatekeeperAction.ExportDataGenerated -> {
            slice.copy(exportData = action.data)
        }

        GatekeeperAction.ClearExportData -> {
            slice.copy(exportData = null)
        }

        is GatekeeperAction.RemoteSyncCompleted -> {
            val localVaultMap = slice.vaultItems.associateBy { it.id }
            val remoteVaultMap = action.newVaultItems.associateBy { it.id }
            val allVaultIds = localVaultMap.keys + remoteVaultMap.keys

            val mergedVaultItems =
                allVaultIds.mapNotNull { id ->
                    val local = localVaultMap[id]
                    val remote = remoteVaultMap[id]
                    when {
                        local != null && remote != null -> if (remote.lastModified > local.lastModified) remote else local
                        remote != null -> remote
                        else -> local
                    }
                }

            val localContentMap = slice.contentItems.associateBy { it.id }
            val remoteContentMap = action.newContentItems.associateBy { it.id }
            val allContentIds = localContentMap.keys + remoteContentMap.keys

            val mergedContentItems =
                allContentIds.mapNotNull { id ->
                    val local = localContentMap[id]
                    val remote = remoteContentMap[id]
                    when {
                        local != null && remote != null -> {
                            if (remote.lastModified >
                                local.lastModified
                            ) {
                                remote.copy(localFilePath = local.localFilePath, downloadStatus = local.downloadStatus)
                            } else {
                                local
                            }
                        }

                        remote != null -> {
                            remote
                        }

                        else -> {
                            local
                        }
                    }
                }

            val newContentMap = mergedContentItems.associateBy { it.id }
            val updatedSlots =
                slice.intentionalSlots.map { slot ->
                    val newContent = newContentMap[slot.contentItem.id]
                    if (newContent != null) slot.copy(contentItem = newContent) else slot
                }

            slice.copy(vaultItems = mergedVaultItems, contentItems = mergedContentItems, intentionalSlots = updatedSlots)
        }

        else -> {
            slice
        }
    }

private fun reduceMedia(
    slice: MediaState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<MediaState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState = when (action) {
        is GatekeeperAction.ProcessSharedLink -> {
            val isSoundCloud = action.url.contains("soundcloud.com", ignoreCase = true)
            val isGeneric = !isSoundCloud && !action.url.contains("youtu", ignoreCase = true)
            effects.add(GatekeeperEffect.FetchUrlMetadata(action.url, action.providedTitle, action.currentTimestamp, isSoundCloud, isGeneric))
            slice.copy(isProcessingLink = true)
        }

        is GatekeeperAction.SaveToContentBank -> {
            slice.copy(isProcessingLink = false)
        }

        is GatekeeperAction.DownloadProgressUpdated -> {
            slice.copy(activeDownloads = slice.activeDownloads + (action.id to action.progress))
        }

        is GatekeeperAction.DownloadCompleted -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.DownloadFailed -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.SaveMediaPosition -> {
            effects.add(GatekeeperEffect.DbSaveMediaPosition(action.mediaId, action.positionSeconds))
            slice.copy(
                savedMediaPositions =
                    slice.savedMediaPositions + (action.mediaId to action.positionSeconds),
            )
        }

        is GatekeeperAction.OpenCleanPlayer -> {
            slice.copy(activeVideoId = action.videoId)
        }

        GatekeeperAction.StopCleanPlayer -> {
            slice.copy(activeVideoId = null)
        }

        is GatekeeperAction.OpenCleanAudioPlayer -> {
            slice.copy(activeAudioUrl = action.url)
        }

        GatekeeperAction.StopCleanAudioPlayer -> {
            slice.copy(activeAudioUrl = null)
        }

        is GatekeeperAction.OpenNativePlayer -> {
            slice.copy(activeNativeMediaItem = action.contentItem)
        }

        GatekeeperAction.CloseNativePlayer -> {
            slice.copy(activeNativeMediaItem = null)
        }

        is GatekeeperAction.SearchPodcastsRequested -> {
            effects.add(GatekeeperEffect.SearchPodcasts(action.query))
            slice.copy(isSearchingPodcasts = true, podcastSearchResults = emptyList())
        }

        is GatekeeperAction.PodcastSearchCompleted -> {
            slice.copy(isSearchingPodcasts = false, podcastSearchResults = action.results)
        }

        is GatekeeperAction.LoadPodcastEpisodes -> {
            effects.add(GatekeeperEffect.DbLoadCachedPodcastEpisodes(action.podcastId))
            effects.add(GatekeeperEffect.FetchPodcastFeedForEpisodes(action.feedUrl, action.podcastId))
            slice.copy(isLoadingEpisodes = true, activePodcastId = action.podcastId)
        }

        is GatekeeperAction.PodcastEpisodesLoaded -> {
            slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = action.episodes, activePodcastId = action.podcastId)
        }

        GatekeeperAction.ClearPodcastEpisodes -> {
            slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = null, activePodcastId = null)
        }

        GatekeeperAction.LoadLatestGlobalEpisodes -> {
            effects.add(GatekeeperEffect.DbLoadLatestGlobalEpisodes)
            slice.copy(isLoadingGlobalEpisodes = true)
        }

        is GatekeeperAction.LatestGlobalEpisodesLoaded -> {
            slice.copy(isLoadingGlobalEpisodes = false, latestGlobalEpisodes = action.episodes)
        }

        is GatekeeperAction.CacheParsedEpisodes -> {
            effects.add(GatekeeperEffect.DbCacheParsedEpisodes(action.episodes, action.podcastId))
            slice
        }

        is GatekeeperAction.AddEpisodeToBank -> {
            effects.add(GatekeeperEffect.EmitAction(
                GatekeeperAction.SaveToContentBank(
                    videoId = action.episode.audioUrl,
                    title = action.episode.title,
                    source = ContentSource.GENERIC,
                    type = ContentType.AUDIO,
                    currentTimestamp = currentTimeMillis(),
                    durationSeconds = action.episode.durationSeconds,
                    channelName = action.podcastTitle,
                    podcastId = action.podcastId,
                )
            ))
            slice
        }

        is GatekeeperAction.OpenSurgicalFacebook -> {
            slice.copy(activeFacebookUrl = action.url)
        }

        GatekeeperAction.CloseSurgicalFacebook -> {
            slice.copy(activeFacebookUrl = null)
        }

        GatekeeperAction.WebEngineInitialized -> {
            slice.copy(isWebEngineReady = true)
        }

        is GatekeeperAction.SurgicalNavigationRequested -> {
            slice.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.SurgicalNavigationCompleted -> {
            slice.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.OpenPinnedWebsite -> {
            slice.copy(activePinnedWebsiteUrl = action.url)
        }

        GatekeeperAction.ClosePinnedWebsite -> {
            slice.copy(activePinnedWebsiteUrl = null)
        }

        else -> {
            slice
        }
    }
                return Update(newState, effects)
}

fun deletedReduce4(
    slice: MediaState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): MediaState =
    when (action) {
        is GatekeeperAction.ProcessSharedLink -> {
            slice.copy(isProcessingLink = true)
        }

        is GatekeeperAction.SaveToContentBank -> {
            slice.copy(isProcessingLink = false)
        }

        is GatekeeperAction.DownloadProgressUpdated -> {
            slice.copy(activeDownloads = slice.activeDownloads + (action.id to action.progress))
        }

        is GatekeeperAction.DownloadCompleted -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.DownloadFailed -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            slice.copy(activeDownloads = slice.activeDownloads - action.id)
        }

        is GatekeeperAction.SaveMediaPosition -> {
            slice.copy(
                savedMediaPositions =
                    slice.savedMediaPositions + (action.mediaId to action.positionSeconds),
            )
        }

        is GatekeeperAction.OpenCleanPlayer -> {
            slice.copy(activeVideoId = action.videoId)
        }

        GatekeeperAction.StopCleanPlayer -> {
            slice.copy(activeVideoId = null)
        }

        is GatekeeperAction.OpenCleanAudioPlayer -> {
            slice.copy(activeAudioUrl = action.url)
        }

        GatekeeperAction.StopCleanAudioPlayer -> {
            slice.copy(activeAudioUrl = null)
        }

        is GatekeeperAction.OpenNativePlayer -> {
            slice.copy(activeNativeMediaItem = action.contentItem)
        }

        GatekeeperAction.CloseNativePlayer -> {
            slice.copy(activeNativeMediaItem = null)
        }

        is GatekeeperAction.SearchPodcastsRequested -> {
            slice.copy(isSearchingPodcasts = true, podcastSearchResults = emptyList())
        }

        is GatekeeperAction.PodcastSearchCompleted -> {
            slice.copy(isSearchingPodcasts = false, podcastSearchResults = action.results)
        }

        is GatekeeperAction.LoadPodcastEpisodes -> {
            slice.copy(isLoadingEpisodes = true, activePodcastId = action.podcastId)
        }

        is GatekeeperAction.PodcastEpisodesLoaded -> {
            slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = action.episodes, activePodcastId = action.podcastId)
        }

        GatekeeperAction.ClearPodcastEpisodes -> {
            slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = null, activePodcastId = null)
        }

        GatekeeperAction.LoadLatestGlobalEpisodes -> {
            slice.copy(isLoadingGlobalEpisodes = true)
        }

        is GatekeeperAction.LatestGlobalEpisodesLoaded -> {
            slice.copy(isLoadingGlobalEpisodes = false, latestGlobalEpisodes = action.episodes)
        }

        is GatekeeperAction.OpenSurgicalFacebook -> {
            slice.copy(activeFacebookUrl = action.url)
        }

        GatekeeperAction.CloseSurgicalFacebook -> {
            slice.copy(activeFacebookUrl = null)
        }

        GatekeeperAction.WebEngineInitialized -> {
            slice.copy(isWebEngineReady = true)
        }

        is GatekeeperAction.SurgicalNavigationRequested -> {
            slice.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.SurgicalNavigationCompleted -> {
            slice.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.OpenPinnedWebsite -> {
            slice.copy(activePinnedWebsiteUrl = action.url)
        }

        GatekeeperAction.ClosePinnedWebsite -> {
            slice.copy(activePinnedWebsiteUrl = null)
        }

        else -> {
            slice
        }
    }

private fun reduceSyncAndIntegration(
    slice: SyncAndIntegrationState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<SyncAndIntegrationState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState = when (action) {
        GatekeeperAction.UpgradeToProTier -> {
            effects.add(GatekeeperEffect.DbUpdateProStatus(true))
            slice.copy(isProTier = true)
        }

        is GatekeeperAction.LoginSuccess -> {
            effects.add(GatekeeperEffect.SaveToken(action.token))
            slice.copy(isAuthenticated = true, jwtToken = action.token)
        }

        GatekeeperAction.Logout -> {
            effects.add(GatekeeperEffect.ClearToken)
            slice.copy(isAuthenticated = false, jwtToken = null)
        }

        is GatekeeperAction.UpdateSyncUrl -> {
            slice.copy(syncServerUrl = action.url)
        }

        is GatekeeperAction.RequestMagicLink -> {
            effects.add(GatekeeperEffect.RequestMagicLink(action.email))
            slice
        }

        is GatekeeperAction.ProcessPodcastUrl -> {
            effects.add(GatekeeperEffect.FetchPodcastFeedForSubscription(action.url))
            slice.copy(isSyncingPodcasts = true, podcastSyncError = null)
        }

        is GatekeeperAction.SavePodcastSubscription -> {
            effects.add(GatekeeperEffect.DbInsertPodcastSubscription(action.subscription))
            effects.add(GatekeeperEffect.FetchPodcastFeedForEpisodes(action.subscription.feedUrl, action.subscription.id))
            slice.copy(
                podcastSubscriptions = slice.podcastSubscriptions + action.subscription,
                isSyncingPodcasts = false,
                podcastSyncError = null,
            )
        }

        GatekeeperAction.ClearPodcastSyncError -> {
            slice.copy(podcastSyncError = null)
        }

        is GatekeeperAction.RemovePodcastSubscription -> {
            effects.add(GatekeeperEffect.DbDeletePodcastSubscription(action.id, action.currentTimestamp))
            slice.copy(
                podcastSubscriptions =
                    slice.podcastSubscriptions.filter {
                        it.id != action.id
                    },
            )
        }

        GatekeeperAction.PodcastSyncStarted -> {
            slice.copy(isSyncingPodcasts = true, podcastSyncError = null)
        }

        GatekeeperAction.PodcastSyncCompleted -> {
            slice.copy(isSyncingPodcasts = false, podcastSyncError = null)
        }

        is GatekeeperAction.PodcastSyncFailed -> {
            slice.copy(isSyncingPodcasts = false, podcastSyncError = action.error)
        }

        is GatekeeperAction.LoadNotificationDigest -> {
            slice.copy(notificationDigest = action.logs)
        }

        GatekeeperAction.ClearNotificationDigest -> {
            effects.add(GatekeeperEffect.DbClearNotificationDigest)
            slice.copy(notificationDigest = emptyList())
        }

        is GatekeeperAction.NotificationIntercepted -> {
            val newLog = NotificationLog(
                packageName = action.packageName,
                title = action.title,
                content = action.content,
                timestamp = action.timestamp,
            )
            effects.add(GatekeeperEffect.DbInsertNotificationDigest(newLog))
            slice.copy(notificationDigest = slice.notificationDigest + newLog)
        }

        GatekeeperAction.RequestBeeperSync -> {
            effects.add(GatekeeperEffect.SyncBeeperChats)
            slice.copy(isSyncingBeeper = true)
        }

        is GatekeeperAction.BeeperChatsLoaded -> {
            slice.copy(isSyncingBeeper = false, beeperChats = action.chats)
        }

        is GatekeeperAction.BeeperSyncFailed -> {
            slice.copy(isSyncingBeeper = false)
        }

        is GatekeeperAction.ScheduleMessage -> {
            effects.add(GatekeeperEffect.DbInsertScheduledMessage(action.message))
            val delayMillis = action.message.scheduledTimestamp - currentTimeMillis()
            effects.add(GatekeeperEffect.ScheduleBeeperMessage(action.message, maxOf(0L, delayMillis)))
            slice.copy(scheduledMessages = slice.scheduledMessages + action.message)
        }

        is GatekeeperAction.CancelScheduledMessage -> {
            effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.CANCELLED))
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id == action.id) {
                            it.copy(status = MessageStatus.CANCELLED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.MessageDelivered -> {
            effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.SENT))
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id == action.id) {
                            it.copy(status = MessageStatus.SENT)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.MessageFailed -> {
            effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.FAILED))
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id == action.id) {
                            it.copy(status = MessageStatus.FAILED)
                        } else {
                            it
                        }
                    },
            )
        }

        GatekeeperAction.RefreshAllFeedsRequested -> {
            effects.add(GatekeeperEffect.EmitAction(GatekeeperAction.PodcastSyncStarted))
            effects.add(GatekeeperEffect.SchedulePodcastRefresh)
            slice
        }

        else -> {
            slice
        }
    }
                return Update(newState, effects)
}

fun deletedReduce5(
    slice: SyncAndIntegrationState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): SyncAndIntegrationState =
    when (action) {
        GatekeeperAction.UpgradeToProTier -> {
            slice.copy(isProTier = true)
        }

        is GatekeeperAction.LoginSuccess -> {
            slice.copy(isAuthenticated = true, jwtToken = action.token)
        }

        GatekeeperAction.Logout -> {
            slice.copy(isAuthenticated = false, jwtToken = null)
        }

        is GatekeeperAction.UpdateSyncUrl -> {
            slice.copy(syncServerUrl = action.url)
        }

        is GatekeeperAction.ProcessPodcastUrl -> {
            slice.copy(isSyncingPodcasts = true, podcastSyncError = null)
        }

        is GatekeeperAction.SavePodcastSubscription -> {
            slice.copy(
                podcastSubscriptions = slice.podcastSubscriptions + action.subscription,
                isSyncingPodcasts = false,
                podcastSyncError = null,
            )
        }

        GatekeeperAction.ClearPodcastSyncError -> {
            slice.copy(podcastSyncError = null)
        }

        is GatekeeperAction.RemovePodcastSubscription -> {
            slice.copy(
                podcastSubscriptions =
                    slice.podcastSubscriptions.filter {
                        it.id !=
                            action.id
                    },
            )
        }

        GatekeeperAction.PodcastSyncStarted -> {
            slice.copy(isSyncingPodcasts = true, podcastSyncError = null)
        }

        GatekeeperAction.PodcastSyncCompleted -> {
            slice.copy(isSyncingPodcasts = false, podcastSyncError = null)
        }

        is GatekeeperAction.PodcastSyncFailed -> {
            slice.copy(isSyncingPodcasts = false, podcastSyncError = action.error)
        }

        is GatekeeperAction.LoadNotificationDigest -> {
            slice.copy(notificationDigest = action.logs)
        }

        GatekeeperAction.ClearNotificationDigest -> {
            slice.copy(notificationDigest = emptyList())
        }

        is GatekeeperAction.NotificationIntercepted -> {
            val newLog =
                NotificationLog(
                    packageName = action.packageName,
                    title = action.title,
                    content = action.content,
                    timestamp = action.timestamp,
                )
            slice.copy(notificationDigest = slice.notificationDigest + newLog)
        }

        GatekeeperAction.RequestBeeperSync -> {
            slice.copy(isSyncingBeeper = true)
        }

        is GatekeeperAction.BeeperChatsLoaded -> {
            slice.copy(isSyncingBeeper = false, beeperChats = action.chats)
        }

        is GatekeeperAction.BeeperSyncFailed -> {
            slice.copy(isSyncingBeeper = false)
        }

        is GatekeeperAction.ScheduleMessage -> {
            slice.copy(scheduledMessages = slice.scheduledMessages + action.message)
        }

        is GatekeeperAction.CancelScheduledMessage -> {
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(status = MessageStatus.CANCELLED)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.MessageDelivered -> {
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(status = MessageStatus.SENT)
                        } else {
                            it
                        }
                    },
            )
        }

        is GatekeeperAction.MessageFailed -> {
            slice.copy(
                scheduledMessages =
                    slice.scheduledMessages.map {
                        if (it.id ==
                            action.id
                        ) {
                            it.copy(status = MessageStatus.FAILED)
                        } else {
                            it
                        }
                    },
            )
        }

        else -> {
            slice
        }
    }
