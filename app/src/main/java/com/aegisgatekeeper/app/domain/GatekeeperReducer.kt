package com.aegisgatekeeper.app.domain

import android.util.Log

/**
 * Pure top-level function. Evaluates actions and returns a new state.
 * No side effects allowed here (no DB calls, no clock reads, no API calls).
 */
fun reduce(
    state: GatekeeperState,
    action: GatekeeperAction,
): GatekeeperState {
    var newState = reduceRulesAndIntercepts(state, action)
    newState = reduceContentAndVault(newState, action)
    newState = reduceSyncAndAuth(newState, action)
    return newState
}

private fun reduceRulesAndIntercepts(
    state: GatekeeperState,
    action: GatekeeperAction,
): GatekeeperState =
    when (action) {
        is GatekeeperAction.RuleViolationDetected -> {
            val whitelist = state.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            val newState = state.copy(activeForegroundApp = action.packageName)

            if (!isWhitelistValid) {
                val wasExpired = whitelist != null
                newState.copy(
                    isOverlayActive = true,
                    currentlyInterceptedApp = action.packageName,
                    expiredSessionDurationMillis = if (wasExpired) whitelist.allocatedDurationMillis else null,
                    activeBlockReason = action.reason,
                )
            } else {
                newState
            }
        }

        is GatekeeperAction.AppBroughtToForeground -> {
            state.copy(activeForegroundApp = action.packageName)
        }

        GatekeeperAction.DismissOverlay -> {
            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                expiredSessionDurationMillis = null,
                activeBlockReason = null,
            )
        }

        is GatekeeperAction.SessionExpired -> {
            state.copy(
                isOverlayActive = true,
                currentlyInterceptedApp = action.packageName,
                expiredSessionDurationMillis = action.allocatedDurationMillis,
                activeWhitelists = state.activeWhitelists - action.packageName,
            )
        }

        GatekeeperAction.LayerOmegaConnected -> {
            state.copy(isLayerOmegaActive = true)
        }

        GatekeeperAction.LayerOmegaDisconnected -> {
            state.copy(isLayerOmegaActive = false)
        }

        // --- Friction & Bypass Logic (The "Uber" Problem) ---
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

            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                activeWhitelists = state.activeWhitelists + (action.packageName to newWhitelist),
                analyticsBypasses = state.analyticsBypasses + 1,
            )
        }

        is GatekeeperAction.LogGiveUp -> {
            // CRITICAL FIX: Grant a 2-second grace period to prevent re-interception
            // during the race between the overlay disappearing and the home intent firing.
            val gracePeriodExpires = action.currentTimestamp + 2000L
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = "GIVE_UP_GRACE_PERIOD",
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = gracePeriodExpires,
                    allocatedDurationMillis = 2000L,
                )

            state.copy(
                analyticsGiveUps = state.analyticsGiveUps + 1,
                activeWhitelists = state.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.LoadNotificationDigest -> {
            state.copy(notificationDigest = action.logs)
        }

        GatekeeperAction.ClearNotificationDigest -> {
            state.copy(notificationDigest = emptyList())
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

            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                activeWhitelists = state.activeWhitelists + (action.packageName to newWhitelist),
                analyticsBypasses = state.analyticsBypasses + 1,
            )
        }

        is GatekeeperAction.WhitelistExpired -> {
            // Clean up the map
            state.copy(
                activeWhitelists = state.activeWhitelists - action.packageName,
            )
        }

        is GatekeeperAction.SetCustomInterceptionMessage -> {
            state.copy(
                customMessages = state.customMessages + (action.packageName to action.message),
            )
        }

        is GatekeeperAction.RemoveCustomInterceptionMessage -> {
            state.copy(
                customMessages = state.customMessages - action.packageName,
            )
        }

        is GatekeeperAction.CreateAppGroup -> {
            val newGroup = AppGroup(id = action.id, name = action.name, apps = action.apps, combinator = action.combinator)
            state.copy(appGroups = state.appGroups + newGroup)
        }

        is GatekeeperAction.UpdateGroupCombinator -> {
            state.copy(
                appGroups =
                    state.appGroups.map {
                        if (it.id == action.groupId) it.copy(combinator = action.combinator) else it
                    },
            )
        }

        is GatekeeperAction.UpdateGroupApps -> {
            state.copy(
                appGroups =
                    state.appGroups.map {
                        if (it.id == action.groupId) it.copy(apps = action.apps) else it
                    },
            )
        }

        is GatekeeperAction.DeleteAppGroup -> {
            state.copy(appGroups = state.appGroups.filter { it.id != action.groupId })
        }

        is GatekeeperAction.AddDomainBlockRule -> {
            val newRule = BlockingRule.DomainBlock(id = action.id, groupId = action.groupId, domains = action.domains)
            state.copy(appGroups = state.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.UpdateDomainBlockRule -> {
            state.copy(
                appGroups =
                    state.appGroups.map { group ->
                        if (group.id == action.groupId) {
                            group.copy(
                                rules =
                                    group.rules.map { rule ->
                                        if (rule.id == action.ruleId && rule is BlockingRule.DomainBlock) {
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
            state.copy(appGroups = state.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.AddScheduledBlockRule -> {
            val newRule =
                BlockingRule.ScheduledBlock(
                    id = action.id,
                    groupId = action.groupId,
                    timeSlots = action.timeSlots,
                    daysOfWeek = action.daysOfWeek,
                )
            state.copy(appGroups = state.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.DeleteRule -> {
            state.copy(
                appGroups =
                    state.appGroups.map {
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
            state.copy(
                appGroups =
                    state.appGroups.map {
                        if (it.id == action.groupId) {
                            it.copy(
                                rules =
                                    it.rules.map { r ->
                                        if (r.id == action.ruleId) {
                                            when (r) {
                                                is BlockingRule.TimeLimit -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.ScheduledBlock -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.CheckIn -> r.copy(isEnabled = action.isEnabled)
                                                is BlockingRule.DomainBlock -> r.copy(isEnabled = action.isEnabled)
                                            }
                                        } else {
                                            r
                                        }
                                    },
                            )
                        } else {
                            it
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
            state.copy(appGroups = state.appGroups.map { if (it.id == action.groupId) it.copy(rules = it.rules + newRule) else it })
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val newLog =
                ConsumedCheckIn(
                    groupId = action.groupId,
                    timeMinutes = action.checkInTimeMinutes,
                    timestamp = action.currentTimestamp,
                )

            val group = state.appGroups.find { it.id == action.groupId }
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

            state.copy(
                consumedCheckIns = state.consumedCheckIns + newLog,
                activeWhitelists = state.activeWhitelists + whitelists,
                analyticsBypasses = if (action.reason != null) state.analyticsBypasses + 1 else state.analyticsBypasses,
            )
        }

        is GatekeeperAction.EndGroupSession -> {
            val group = state.appGroups.find { it.id == action.groupId }
            val appsToRemove = group?.apps ?: emptySet()
            state.copy(
                activeWhitelists = state.activeWhitelists.filterKeys { it !in appsToRemove },
            )
        }

        is GatekeeperAction.SetFrictionGame -> {
            state.copy(activeFrictionGame = action.game)
        }

        is GatekeeperAction.SetManualLockdown -> {
            state.copy(isManualLockdownActive = action.isActive)
        }

        is GatekeeperAction.UpdateMissionControlApps -> {
            state.copy(missionControlApps = action.packageNames)
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = AlternativeActivity(
                description = action.description,
                createdAtTimestamp = action.currentTimestamp,
            )
            state.copy(alternativeActivities = state.alternativeActivities + newActivity)
        }

        is GatekeeperAction.RemoveAlternativeActivity -> {
            state.copy(alternativeActivities = state.alternativeActivities.filter { it.id != action.id })
        }

        // --- Metacognition Logic ---
        is GatekeeperAction.LogSessionMetacognition -> {
            val newLog =
                SessionLog(
                    packageName = action.packageName,
                    durationMillis = action.durationMillis,
                    emotion = action.emotion,
                    loggedAtTimestamp = action.currentTimestamp,
                )
            state.copy(sessionLogs = state.sessionLogs + newLog)
        }

        // --- Permission Flow ---
        is GatekeeperAction.PermissionsUpdated -> {
            state.copy(
                hasOverlayPermission = action.hasOverlay,
                hasUsageAccessPermission = action.hasUsageAccess,
                hasAccessibilityPermission = action.hasAccessibility,
                isBatteryOptimizationDisabled = action.isBatteryDisabled,
            )
        }

        GatekeeperAction.ReengageShields -> {
            state.copy(activeWhitelists = emptyMap())
        }

        else -> {
            state
        }
    }

private fun reduceContentAndVault(
    state: GatekeeperState,
    action: GatekeeperAction,
): GatekeeperState =
    when (action) {
        // --- Vault Logic ---
        is GatekeeperAction.SaveToVault -> {
            val newItem =
                VaultItem(
                    query = action.query,
                    capturedAtTimestamp = action.currentTimestamp,
                    lastModified = action.currentTimestamp,
                )
            state.copy(vaultItems = state.vaultItems + newItem)
        }

        is GatekeeperAction.MarkVaultItemResolved -> {
            state.copy(
                vaultItems =
                    state.vaultItems.map {
                        if (it.id == action.id) it.copy(isResolved = true, lastModified = action.currentTimestamp) else it
                    },
            )
        }

        // --- Content Bank Logic ---
        is GatekeeperAction.ProcessSharedLink -> {
            state.copy(isProcessingLink = true)
        }

        is GatekeeperAction.SaveToContentBank -> {
            val existing = state.contentItems.find { it.videoId == action.videoId && it.source == action.source }
            if (existing != null) {
                val updatedItem = existing.copy(
                    title = action.title,
                    durationSeconds = action.durationSeconds ?: existing.durationSeconds,
                    lastModified = action.currentTimestamp,
                    isDeleted = false
                )
                state.copy(
                    contentItems = state.contentItems.map { if (it.id == existing.id) updatedItem else it },
                    intentionalSlots = state.intentionalSlots.map { slot ->
                        if (slot.contentItem.id == existing.id) slot.copy(contentItem = updatedItem) else slot
                    },
                    isProcessingLink = false
                )
            } else {
                val newRank = state.contentItems.size.toLong()
                val newItem =
                    ContentItem(
                        videoId = action.videoId,
                        title = action.title,
                        source = action.source,
                        type = action.type,
                        rank = newRank,
                        capturedAtTimestamp = action.currentTimestamp,
                        durationSeconds = action.durationSeconds,
                        lastModified = action.currentTimestamp,
                    )
                state.copy(
                    contentItems = state.contentItems + newItem,
                    isProcessingLink = false,
                )
            }
        }

        is GatekeeperAction.ReorderContentBank -> {
            if (action.fromIndex !in state.contentItems.indices || action.toIndex !in state.contentItems.indices) {
                state
            } else {
                val mutableList = state.contentItems.toMutableList()
                val itemToMove = mutableList.removeAt(action.fromIndex)
                mutableList.add(action.toIndex, itemToMove)

                // Reassign ranks based on new array indices
                val updatedList =
                    mutableList.mapIndexed { index, item ->
                        item.copy(rank = index.toLong(), lastModified = action.currentTimestamp)
                    }
                state.copy(contentItems = updatedList)
            }
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            state.copy(
                contentItems =
                    state.contentItems.map {
                        if (it.id == action.id) it.copy(isDeleted = true, lastModified = action.currentTimestamp) else it
                    },
                intentionalSlots = state.intentionalSlots.filter { it.contentItem.id != action.id }
            )
        }

        is GatekeeperAction.UpdateContentFilter -> {
            state.copy(activeContentFilter = action.filter)
        }

        // --- YouTube Clean Room Logic ---
        is GatekeeperAction.SearchYouTubeRequested -> {
            state.copy(isLoadingYouTube = true, youtubeSearchResults = emptyList())
        }

        is GatekeeperAction.YouTubeSearchCompleted -> {
            state.copy(isLoadingYouTube = false, youtubeSearchResults = action.results)
        }

        is GatekeeperAction.YouTubeSearchFailed -> {
            state.copy(isLoadingYouTube = false)
        }

        is GatekeeperAction.SaveMediaPosition -> {
            state.copy(savedMediaPositions = state.savedMediaPositions + (action.mediaId to action.positionSeconds))
        }

        is GatekeeperAction.OpenCleanPlayer -> {
            state.copy(activeVideoId = action.videoId)
        }

        GatekeeperAction.CloseCleanPlayer -> {
            state.copy(activeVideoId = null)
        }

        // --- Intentional Content Slots ---
        is GatekeeperAction.SaveIntentionalSlot -> {
            val newItem = IntentionalSlotItem(slotIndex = action.slotIndex, contentItem = action.contentItem)
            val newList = state.intentionalSlots.filter { it.slotIndex != action.slotIndex } + newItem
            state.copy(intentionalSlots = newList)
        }

        is GatekeeperAction.ClearIntentionalSlot -> {
            state.copy(intentionalSlots = state.intentionalSlots.filter { it.slotIndex != action.slotIndex })
        }

        is GatekeeperAction.OpenCleanAudioPlayer -> {
            state.copy(activeAudioUrl = action.url)
        }

        GatekeeperAction.CloseCleanAudioPlayer -> {
            state.copy(activeAudioUrl = null)
        }

        is GatekeeperAction.OpenSurgicalFacebook -> {
            state.copy(activeFacebookUrl = action.url)
        }

        GatekeeperAction.CloseSurgicalFacebook -> {
            state.copy(activeFacebookUrl = null)
        }

        GatekeeperAction.WebEngineInitialized -> {
            state.copy(isWebEngineReady = true)
        }

        is GatekeeperAction.SurgicalNavigationRequested -> {
            state.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.SurgicalNavigationCompleted -> {
            state.copy(currentSurgicalUrl = action.url)
        }

        is GatekeeperAction.OpenPinnedWebsite -> {
            state.copy(activePinnedWebsiteUrl = action.url)
        }

        GatekeeperAction.ClosePinnedWebsite -> {
            state.copy(activePinnedWebsiteUrl = null)
        }

        is GatekeeperAction.AddPinnedWebsite -> {
            val newWebsite = PinnedWebsite(action.id, action.label, action.url)
            state.copy(missionControlWebsites = state.missionControlWebsites + newWebsite)
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            state.copy(missionControlWebsites = state.missionControlWebsites.filter { it.id != action.id })
        }

        else -> {
            state
        }
    }

private fun reduceSyncAndAuth(
    state: GatekeeperState,
    action: GatekeeperAction,
): GatekeeperState =
    when (action) {
        GatekeeperAction.GenerateExportData -> {
            state
        }

        is GatekeeperAction.ExportDataGenerated -> {
            state.copy(exportData = action.data)
        }

        GatekeeperAction.ClearExportData -> {
            state.copy(exportData = null)
        }

        GatekeeperAction.UpgradeToProTier -> {
            state.copy(isProTier = true)
        }

        is GatekeeperAction.RequestMagicLink -> {
            // No state change, this is a pure side-effect
            state
        }

        is GatekeeperAction.LoginSuccess -> {
            state.copy(
                isAuthenticated = true,
                jwtToken = action.token,
            )
        }

        GatekeeperAction.Logout -> {
            state.copy(isAuthenticated = false, jwtToken = null)
        }

        is GatekeeperAction.UpdateSyncUrl -> {
            state.copy(syncServerUrl = action.url)
        }

        is GatekeeperAction.RemoteSyncCompleted -> {
            // Implement Last-Write-Wins (LWW) conflict resolution
            val localVaultMap = state.vaultItems.associateBy { it.id }
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

            val localContentMap = state.contentItems.associateBy { it.id }
            val remoteContentMap = action.newContentItems.associateBy { it.id }
            val allContentIds = localContentMap.keys + remoteContentMap.keys

            val mergedContentItems =
                allContentIds.mapNotNull { id ->
                    val local = localContentMap[id]
                    val remote = remoteContentMap[id]
                    when {
                        local != null && remote != null -> if (remote.lastModified > local.lastModified) remote else local
                        remote != null -> remote
                        else -> local
                    }
                }

            val newContentMap = mergedContentItems.associateBy { it.id }
            val updatedSlots = state.intentionalSlots.map { slot ->
                val newContent = newContentMap[slot.contentItem.id]
                if (newContent != null) slot.copy(contentItem = newContent) else slot
            }

            state.copy(
                vaultItems = mergedVaultItems,
                contentItems = mergedContentItems,
                intentionalSlots = updatedSlots,
            )
        }

        else -> {
            state
        }
    }
