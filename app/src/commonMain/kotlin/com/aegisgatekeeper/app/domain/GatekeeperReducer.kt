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
                val prevApp = slice.activeForegroundApp
                val whitelist = slice.activeWhitelists[action.packageName]
                val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

                val interviewApp =
                    if (prevApp != null && prevApp != action.packageName) {
                        val prevWhitelist = slice.activeWhitelists[prevApp]
                        if (prevWhitelist != null &&
                            action.currentTimestamp < prevWhitelist.expiresAtTimestamp &&
                            prevWhitelist.reason != "GIVE_UP_GRACE_PERIOD"
                        ) {
                            prevApp
                        } else {
                            null
                        }
                    } else {
                        null
                    }

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
                        pendingExitInterview = interviewApp ?: slice.pendingExitInterview,
                    )
                } else {
                    newSlice.copy(
                        isOverlayActive = interviewApp != null || slice.isOverlayActive,
                        pendingExitInterview = interviewApp ?: slice.pendingExitInterview,
                    )
                }
            }

            is GatekeeperAction.AppBroughtToForeground -> {
                val prevApp = slice.activeForegroundApp
                val whitelist = slice.activeWhitelists[action.packageName]
                val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

                // Logic for triggering Exit Interview moved from Service to Reducer
                val interviewApp =
                    if (prevApp != null && prevApp != action.packageName) {
                        val prevWhitelist = slice.activeWhitelists[prevApp]
                        if (prevWhitelist != null &&
                            action.currentTimestamp < prevWhitelist.expiresAtTimestamp &&
                            prevWhitelist.reason != "GIVE_UP_GRACE_PERIOD"
                        ) {
                            prevApp
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                if (slice.pendingExitInterview != null || interviewApp != null) {
                    slice.copy(
                        activeForegroundApp = action.packageName,
                        isOverlayActive = interviewApp != null || slice.isOverlayActive,
                        pendingExitInterview = interviewApp ?: slice.pendingExitInterview,
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

            is GatekeeperAction.GrantTemporaryCallWhitelist -> {
                val expiresAt = action.currentTimestamp + 15_000L // 15-second grace period
                val newWhitelist =
                    TemporaryWhitelist(
                        packageName = action.packageName,
                        reason = "INCOMING_CALL",
                        grantedAtTimestamp = action.currentTimestamp,
                        expiresAtTimestamp = expiresAt,
                        allocatedDurationMillis = 15_000L,
                    )
                slice.copy(activeWhitelists = slice.activeWhitelists + (action.packageName to newWhitelist))
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

private fun reduceData(
    slice: DataState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<DataState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState =
        when (action) {
            is GatekeeperAction.SaveToVault -> {
                val item =
                    VaultItem(
                        id = randomUUIDString(),
                        query = action.query,
                        capturedAtTimestamp = action.currentTimestamp,
                    )
                effects.add(GatekeeperEffect.DbInsertVaultItem(item))
                slice.copy(vaultItems = listOf(item) + slice.vaultItems)
            }

            is GatekeeperAction.MarkVaultItemResolved -> {
                effects.add(GatekeeperEffect.DbMarkVaultItemResolved(action.id, action.currentTimestamp))
                slice.copy(vaultItems = slice.vaultItems.map { if (it.id == action.id) it.copy(isResolved = true) else it })
            }

            is GatekeeperAction.AddEpisodeToBank -> {
                val newRank = (slice.contentItems.maxOfOrNull { it.rank } ?: -1L) + 1L
                val item =
                    ContentItem(
                        id = randomUUIDString(),
                        podcastId = action.podcastId,
                        videoId = action.episode.audioUrl,
                        title = action.episode.title,
                        source = ContentSource.GENERIC,
                        type = ContentType.AUDIO,
                        rank = newRank,
                        capturedAtTimestamp = currentTimeMillis(),
                        durationSeconds = action.episode.durationSeconds,
                        channelName = action.podcastTitle,
                    )
                effects.add(GatekeeperEffect.DbUpsertContentItem(item))
                slice.copy(contentItems = slice.contentItems + item)
            }

            is GatekeeperAction.SaveToContentBank -> {
                val newRank = (slice.contentItems.maxOfOrNull { it.rank } ?: -1L) + 1L
                val item =
                    ContentItem(
                        id = randomUUIDString(),
                        podcastId = action.podcastId,
                        videoId = action.videoId,
                        title = action.title,
                        source = action.source,
                        type = action.type,
                        rank = newRank,
                        capturedAtTimestamp = action.currentTimestamp,
                        durationSeconds = action.durationSeconds,
                        channelName = action.channelName,
                    )
                effects.add(GatekeeperEffect.DbUpsertContentItem(item))
                slice.copy(contentItems = slice.contentItems + item)
            }

            is GatekeeperAction.ReorderContentBank -> {
                val mutableList = slice.contentItems.toMutableList()
                val item = mutableList.removeAt(action.fromIndex)
                mutableList.add(action.toIndex, item)
                val newList =
                    mutableList.mapIndexed { index, contentItem ->
                        contentItem.copy(rank = index.toLong())
                    }
                newList.forEach {
                    effects.add(GatekeeperEffect.DbUpdateRank(it.id, it.rank, action.currentTimestamp))
                }
                slice.copy(contentItems = newList)
            }

            is GatekeeperAction.RemoveFromContentBank -> {
                effects.add(GatekeeperEffect.DbRemoveFromContentBank(action.id, action.currentTimestamp))
                slice.copy(contentItems = slice.contentItems.map { if (it.id == action.id) it.copy(isDeleted = true) else it })
            }

            is GatekeeperAction.DownloadMediaRequested -> {
                val item = slice.contentItems.find { it.id == action.id }
                if (item != null) effects.add(GatekeeperEffect.DownloadMedia(action.id, item.videoId))
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
                val status = if (action.progress >= 100f) DownloadStatus.COMPLETED else DownloadStatus.DOWNLOADING
                slice.copy(contentItems = slice.contentItems.map { if (it.id == action.id) it.copy(downloadStatus = status) else it })
            }

            is GatekeeperAction.DownloadCompleted -> {
                effects.add(
                    GatekeeperEffect.DbUpdateDownloadStatus(action.id, DownloadStatus.COMPLETED, action.localFilePath, currentTimeMillis()),
                )
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

            is GatekeeperAction.DeleteDownloadedMedia -> {
                effects.add(GatekeeperEffect.DeleteDownloadedMedia(action.id))
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
                val podcastId = action.id
                val itemsToDelete = slice.contentItems.filter { it.podcastId == podcastId }
                val itemIdsToDelete = itemsToDelete.map { it.id }
                slice.copy(
                    contentItems =
                        slice.contentItems.map {
                            if (it.podcastId ==
                                podcastId
                            ) {
                                it.copy(isDeleted = true, lastModified = action.currentTimestamp)
                            } else {
                                it
                            }
                        },
                    intentionalSlots = slice.intentionalSlots.filter { it.contentItem.id !in itemIdsToDelete },
                )
            }

            is GatekeeperAction.RemoteSyncCompleted -> {
                val vaultMap = slice.vaultItems.associateBy { it.id }.toMutableMap()
                action.newVaultItems.forEach {
                    val current = vaultMap[it.id]
                    if (current == null || current.lastModified < it.lastModified) {
                        vaultMap[it.id] = it
                    }
                }
                val contentMap = slice.contentItems.associateBy { it.id }.toMutableMap()
                action.newContentItems.forEach { remote ->
                    val current = contentMap[remote.id]
                    if (current == null || current.lastModified < remote.lastModified) {
                        contentMap[remote.id] =
                            if (current != null) {
                                remote.copy(localFilePath = current.localFilePath, downloadStatus = current.downloadStatus)
                            } else {
                                remote
                            }
                    }
                }
                effects.add(GatekeeperEffect.DbRemoteSyncCompleted(action.newVaultItems, action.newContentItems))
                slice.copy(vaultItems = vaultMap.values.toList(), contentItems = contentMap.values.toList().sortedBy { it.rank })
            }

            is GatekeeperAction.SaveIntentionalSlot -> {
                effects.add(GatekeeperEffect.DbInsertIntentionalSlot(action.slotIndex, action.contentItem.id))
                val newSlots =
                    slice.intentionalSlots.filter { it.slotIndex != action.slotIndex } +
                        IntentionalSlotItem(action.slotIndex, action.contentItem)
                slice.copy(intentionalSlots = newSlots)
            }

            is GatekeeperAction.ClearIntentionalSlot -> {
                effects.add(GatekeeperEffect.DbClearIntentionalSlot(action.slotIndex))
                slice.copy(intentionalSlots = slice.intentionalSlots.filter { it.slotIndex != action.slotIndex })
            }

            is GatekeeperAction.LogGiveUp -> {
                slice.copy(analyticsGiveUps = slice.analyticsGiveUps + 1)
            }

            is GatekeeperAction.FrictionCompleted -> {
                slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
            }

            is GatekeeperAction.EmergencyBypassRequested -> {
                slice.copy(analyticsBypasses = slice.analyticsBypasses + 1)
            }

            is GatekeeperAction.UpdatePhaseWindows -> {
                effects.add(
                    GatekeeperEffect.DbUpdatePhaseWindows(
                        action.deepWorkStartMinutes,
                        action.deepWorkEndMinutes,
                        action.gatheringStartMinutes,
                        action.gatheringEndMinutes,
                    ),
                )
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
                val site = PinnedWebsite(action.id, action.label, action.url)
                effects.add(GatekeeperEffect.DbAddPinnedWebsite(site, slice.missionControlWebsites.size.toLong()))
                slice.copy(missionControlWebsites = slice.missionControlWebsites + site)
            }

            is GatekeeperAction.RemovePinnedWebsite -> {
                effects.add(GatekeeperEffect.DbRemovePinnedWebsite(action.id))
                slice.copy(missionControlWebsites = slice.missionControlWebsites.filter { it.id != action.id })
            }

            is GatekeeperAction.AddAlternativeActivity -> {
                val act = AlternativeActivity(description = action.description, createdAtTimestamp = action.currentTimestamp)
                effects.add(GatekeeperEffect.DbAddAlternativeActivity(act))
                slice.copy(alternativeActivities = slice.alternativeActivities + act)
            }

            is GatekeeperAction.RemoveAlternativeActivity -> {
                effects.add(GatekeeperEffect.DbRemoveAlternativeActivity(action.id))
                slice.copy(alternativeActivities = slice.alternativeActivities.filter { it.id != action.id })
            }

            is GatekeeperAction.SetCustomInterceptionMessage -> {
                effects.add(GatekeeperEffect.DbSetCustomInterceptionMessage(action.packageName, action.message))
                slice.copy(customMessages = slice.customMessages + (action.packageName to action.message))
            }

            is GatekeeperAction.RemoveCustomInterceptionMessage -> {
                effects.add(GatekeeperEffect.DbRemoveCustomInterceptionMessage(action.packageName))
                slice.copy(customMessages = slice.customMessages - action.packageName)
            }

            is GatekeeperAction.RedeemCheckInToken -> {
                val log =
                    ConsumedCheckIn(groupId = action.groupId, timeMinutes = action.checkInTimeMinutes, timestamp = action.currentTimestamp)
                slice.copy(consumedCheckIns = slice.consumedCheckIns + log)
            }

            is GatekeeperAction.ResetCheckIns -> {
                slice.copy(consumedCheckIns = slice.consumedCheckIns.filter { it.groupId != action.groupId })
            }

            is GatekeeperAction.ClearExportData -> {
                slice.copy(exportData = null)
            }

            is GatekeeperAction.GenerateExportData -> {
                effects.add(GatekeeperEffect.GenerateExportData)
                slice
            }

            is GatekeeperAction.ExportDataGenerated -> {
                slice.copy(exportData = action.data)
            }

            is GatekeeperAction.TriggerMetacognition -> {
                slice.copy(pendingMetacognition = MetacognitionRequest(action.packageName, action.durationMillis))
            }

            is GatekeeperAction.ClearMetacognition -> {
                slice.copy(pendingMetacognition = null)
            }

            is GatekeeperAction.LogSessionMetacognition -> {
                val log =
                    SessionLog(
                        packageName = action.packageName,
                        durationMillis = action.durationMillis,
                        emotion = action.emotion,
                        loggedAtTimestamp = action.currentTimestamp,
                    )
                effects.add(GatekeeperEffect.DbInsertSessionLog(log))
                slice.copy(sessionLogs = slice.sessionLogs + log, pendingMetacognition = null)
            }

            else -> {
                slice
            }
        }
    return Update(newState, effects)
}

private fun reduceMedia(
    slice: MediaState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<MediaState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState =
        when (action) {
            is GatekeeperAction.ProcessSharedLink -> {
                effects.add(
                    GatekeeperEffect.FetchUrlMetadata(
                        action.url,
                        action.providedTitle,
                        action.currentTimestamp,
                        action.url.contains("soundcloud.com", ignoreCase = true),
                        !action.url.contains("youtu.be", ignoreCase = true) && !action.url.contains("youtube.com", ignoreCase = true) &&
                            !action.url.contains("soundcloud.com", ignoreCase = true),
                    ),
                )
                slice.copy(isProcessingLink = true)
            }

            is GatekeeperAction.SaveToContentBank -> {
                slice.copy(isProcessingLink = false)
            }

            is GatekeeperAction.SearchPodcastsRequested -> {
                effects.add(GatekeeperEffect.SearchPodcasts(action.query))
                slice.copy(isSearchingPodcasts = true)
            }

            is GatekeeperAction.PodcastSearchCompleted -> {
                slice.copy(isSearchingPodcasts = false, podcastSearchResults = action.results)
            }

            is GatekeeperAction.ProcessPodcastUrl -> {
                effects.add(GatekeeperEffect.FetchPodcastFeedForSubscription(action.url))
                slice
            }

            is GatekeeperAction.LoadPodcastEpisodes -> {
                effects.add(GatekeeperEffect.FetchPodcastFeedForEpisodes(action.feedUrl, action.podcastId))
                slice.copy(isLoadingEpisodes = true, activePodcastId = action.podcastId)
            }

            is GatekeeperAction.PodcastEpisodesLoaded -> {
                slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = action.episodes, activePodcastId = action.podcastId)
            }

            is GatekeeperAction.ClearPodcastEpisodes -> {
                slice.copy(isLoadingEpisodes = false, activePodcastEpisodes = null, activePodcastId = null)
            }

            is GatekeeperAction.CacheParsedEpisodes -> {
                effects.add(GatekeeperEffect.DbCacheParsedEpisodes(action.episodes, action.podcastId))
                slice
            }

            is GatekeeperAction.LoadLatestGlobalEpisodes -> {
                effects.add(GatekeeperEffect.DbLoadLatestGlobalEpisodes)
                slice.copy(isLoadingGlobalEpisodes = true)
            }

            is GatekeeperAction.LatestGlobalEpisodesLoaded -> {
                slice.copy(isLoadingGlobalEpisodes = false, latestGlobalEpisodes = action.episodes)
            }

            is GatekeeperAction.DownloadProgressUpdated -> {
                slice.copy(activeDownloads = slice.activeDownloads + (action.id to action.progress))
            }

            is GatekeeperAction.DownloadCompleted -> {
                slice.copy(activeDownloads = slice.activeDownloads - action.id)
            }

            is GatekeeperAction.DeleteDownloadedMedia -> {
                slice.copy(activeDownloads = slice.activeDownloads - action.id)
            }

            is GatekeeperAction.SaveMediaPosition -> {
                if (action.positionSeconds == 0f) {
                    slice.copy(savedMediaPositions = slice.savedMediaPositions + (action.mediaId to 0f))
                } else {
                    effects.add(GatekeeperEffect.DbSaveMediaPosition(action.mediaId, action.positionSeconds))
                    slice.copy(savedMediaPositions = slice.savedMediaPositions + (action.mediaId to action.positionSeconds))
                }
            }

            is GatekeeperAction.PlayYouTubeVideo -> {
                val item = fullState.data.contentItems.find { it.videoId == action.videoId }
                if (item != null) {
                    effects.add(GatekeeperEffect.FetchYouTubeStream(item))
                }
                slice.copy(extractingYouTubeVideoId = action.videoId)
            }

            is GatekeeperAction.OpenCleanAudioPlayer -> {
                slice.copy(activeAudioUrl = action.url, isAudioPlayerMaximized = true)
            }

            is GatekeeperAction.MinimizeCleanAudioPlayer -> {
                slice.copy(isAudioPlayerMaximized = false)
            }

            is GatekeeperAction.StopCleanAudioPlayer -> {
                slice.copy(activeAudioUrl = null, isAudioPlayerMaximized = false)
            }

            is GatekeeperAction.OpenNativePlayer -> {
                slice.copy(activeNativeMediaItem = action.contentItem, isNativePlayerMaximized = true, extractingYouTubeVideoId = null)
            }

            is GatekeeperAction.MinimizeNativePlayer -> {
                slice.copy(isNativePlayerMaximized = false)
            }

            is GatekeeperAction.CloseNativePlayer -> {
                slice.copy(activeNativeMediaItem = null, isNativePlayerMaximized = false)
            }

            is GatekeeperAction.OpenSurgicalFacebook -> {
                slice.copy(activeFacebookUrl = action.url)
            }

            is GatekeeperAction.CloseSurgicalFacebook -> {
                slice.copy(activeFacebookUrl = null)
            }

            is GatekeeperAction.OpenPinnedWebsite -> {
                slice.copy(activePinnedWebsiteUrl = action.url)
            }

            is GatekeeperAction.ClosePinnedWebsite -> {
                slice.copy(activePinnedWebsiteUrl = null)
            }

            is GatekeeperAction.SurgicalNavigationRequested -> {
                slice.copy(currentSurgicalUrl = action.url)
            }

            is GatekeeperAction.WebEngineInitialized -> {
                slice.copy(isWebEngineReady = true)
            }

            is GatekeeperAction.YouTubeExtractionFailed -> {
                if (slice.extractingYouTubeVideoId == action.videoId) {
                    slice.copy(extractingYouTubeVideoId = null)
                } else {
                    slice
                }
            }

            else -> {
                slice
            }
        }
    return Update(newState, effects)
}

private fun reduceSyncAndIntegration(
    slice: SyncAndIntegrationState,
    action: GatekeeperAction,
    fullState: GatekeeperState,
): Update<SyncAndIntegrationState> {
    val effects = mutableSetOf<GatekeeperEffect>()
    val newState =
        when (action) {
            is GatekeeperAction.UpgradeToProTier -> {
                effects.add(GatekeeperEffect.DbUpdateProStatus(true))
                slice.copy(isProTier = true)
            }

            is GatekeeperAction.UpdateSyncUrl -> {
                slice.copy(syncServerUrl = action.url)
            }

            is GatekeeperAction.LoginSuccess -> {
                effects.add(GatekeeperEffect.SaveToken(action.token))
                slice.copy(isAuthenticated = true, jwtToken = action.token)
            }

            is GatekeeperAction.Logout -> {
                effects.add(GatekeeperEffect.ClearToken)
                slice.copy(isAuthenticated = false, jwtToken = null)
            }

            is GatekeeperAction.RequestMagicLink -> {
                effects.add(GatekeeperEffect.RequestMagicLink(action.email))
                slice
            }

            is GatekeeperAction.SavePodcastSubscription -> {
                effects.add(GatekeeperEffect.DbInsertPodcastSubscription(action.subscription))
                slice.copy(podcastSubscriptions = slice.podcastSubscriptions + action.subscription)
            }

            is GatekeeperAction.RemovePodcastSubscription -> {
                effects.add(GatekeeperEffect.DbDeletePodcastSubscription(action.id, action.currentTimestamp))
                slice.copy(podcastSubscriptions = slice.podcastSubscriptions.filter { it.id != action.id })
            }

            is GatekeeperAction.ProcessPodcastUrl -> {
                slice.copy(isSyncingPodcasts = true, podcastSyncError = null)
            }

            is GatekeeperAction.RefreshAllFeedsRequested -> {
                effects.add(GatekeeperEffect.EmitAction(GatekeeperAction.PodcastSyncStarted))
                effects.add(GatekeeperEffect.SchedulePodcastRefresh)
                slice
            }

            is GatekeeperAction.PodcastSyncStarted -> {
                slice.copy(isSyncingPodcasts = true)
            }

            is GatekeeperAction.PodcastSyncCompleted -> {
                slice.copy(isSyncingPodcasts = false, podcastSyncError = null)
            }

            is GatekeeperAction.PodcastSyncFailed -> {
                slice.copy(isSyncingPodcasts = false, podcastSyncError = action.error)
            }

            is GatekeeperAction.ClearPodcastSyncError -> {
                slice.copy(podcastSyncError = null)
            }

            is GatekeeperAction.LoadNotificationDigest -> {
                slice.copy(notificationDigest = action.logs)
            }

            is GatekeeperAction.ClearNotificationDigest -> {
                effects.add(GatekeeperEffect.DbClearNotificationDigest)
                slice.copy(notificationDigest = emptyList())
            }

            is GatekeeperAction.NotificationIntercepted -> {
                val log =
                    NotificationLog(
                        packageName = action.packageName,
                        title = action.title,
                        content = action.content,
                        timestamp = action.timestamp,
                    )
                effects.add(GatekeeperEffect.DbInsertNotificationDigest(log))
                slice.copy(notificationDigest = listOf(log) + slice.notificationDigest)
            }

            is GatekeeperAction.RequestBeeperSync -> {
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
                effects.add(GatekeeperEffect.ScheduleBeeperMessage(action.message, action.message.scheduledTimestamp - currentTimeMillis()))
                slice.copy(scheduledMessages = slice.scheduledMessages + action.message)
            }

            is GatekeeperAction.CancelScheduledMessage -> {
                effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.CANCELLED))
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
                effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.SENT))
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
                effects.add(GatekeeperEffect.DbUpdateScheduledMessageStatus(action.id, MessageStatus.FAILED))
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
    return Update(newState, effects)
}
