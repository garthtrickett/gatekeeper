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
        state =
            state.copy(
                interception = interception,
                data = data,
                media = media,
                sync = sync,
            ),
        effects = effects,
    )
}

private fun reduceInterception(
    slice: InterceptionState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<InterceptionState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState =
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
                effects.add(
                    GatekeeperEffect.DbAddCheckInRule(
                        action.id,
                        action.groupId,
                        action.checkInTimesMinutes,
                        action.durationMinutes,
                        action.daysOfWeek,
                    ),
                )
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
                effects.add(
                    GatekeeperEffect.DbUpdateCheckInRule(action.id, action.checkInTimesMinutes, action.durationMinutes, action.daysOfWeek),
                )
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

                val newLog =
                    ConsumedCheckIn(groupId = action.groupId, timeMinutes = action.checkInTimeMinutes, timestamp = action.currentTimestamp)
                effects.add(GatekeeperEffect.DbRedeemCheckInToken(newLog, action.groupId, action.reason, action.currentTimestamp))

                if (dismissed) {
                    effects.add(
                        GatekeeperEffect.LaunchAppAndStartSession(slice.currentlyInterceptedApp!!, action.durationMinutes * 60_000L),
                    )
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

