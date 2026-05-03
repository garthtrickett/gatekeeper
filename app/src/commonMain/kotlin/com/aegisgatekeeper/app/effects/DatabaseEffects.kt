package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.platformLog
import com.aegisgatekeeper.app.domain.randomUUIDString
import com.aegisgatekeeper.app.domain.currentTimeMillis

fun handleDatabaseEffects(
    action: GatekeeperAction,
    oldState: GatekeeperState,
    newState: GatekeeperState,
    db: GatekeeperDatabase,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (action) {
        is GatekeeperAction.SaveToVault -> {
            val newItem = (newState.vaultItems - oldState.vaultItems.toSet()).firstOrNull()
            newItem?.let {
                platformLog("Gatekeeper", "🗄️ DB: Inserting new VaultItem: ${it.id}")
                db.vaultItemQueries.insert(
                    id = it.id,
                    query = it.query,
                    capturedAtTimestamp = it.capturedAtTimestamp,
                    isResolved = it.isResolved,
                    lastModified = it.lastModified,
                    isSynced = it.isSynced,
                    isDeleted = it.isDeleted,
                )
            }
        }

        is GatekeeperAction.MarkVaultItemResolved -> {
            platformLog("Gatekeeper", "🗄️ DB: Marking VaultItem as resolved: ${action.id}")
            db.vaultItemQueries.markAsResolved(
                lastModified = action.currentTimestamp,
                id = action.id,
            )
        }

        is GatekeeperAction.SavePodcastSubscription -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting PodcastSubscription: ${action.subscription.showTitle}")
            db.podcastSubscriptionQueries.insert(
                id = action.subscription.id,
                feedUrl = action.subscription.feedUrl,
                showTitle = action.subscription.showTitle,
                artworkUrl = action.subscription.artworkUrl,
            )
        }

        is GatekeeperAction.RemovePodcastSubscription -> {
            platformLog("Gatekeeper", "🗄️ DB: Deleting PodcastSubscription: ${action.id}")
            db.transaction {
                db.podcastSubscriptionQueries.delete(action.id)
                val itemsToDelete = oldState.contentItems.filter { it.podcastId == action.id }
                val slots = db.intentionalSlotQueries.selectAll().executeAsList()
                itemsToDelete.forEach { item ->
                    db.contentItemQueries.delete(lastModified = action.currentTimestamp, id = item.id)
                    slots.forEach { slot ->
                        if (slot.id == item.id) {
                            db.intentionalSlotQueries.delete(slot.slotIndex)
                        }
                    }
                }
            }
        }

        GatekeeperAction.PodcastSyncCompleted -> {
            platformLog("Gatekeeper", "🗄️ DB: Podcast sync completed")
        }

        is GatekeeperAction.SaveToContentBank -> {
            val updatedOrNewItem = newState.contentItems.find { it.videoId == action.videoId && it.source == action.source }
            updatedOrNewItem?.let {
                platformLog("Gatekeeper", "🗄️ DB: Upserting ContentItem: ${it.title}")
                db.contentItemQueries.insert(
                    id = it.id,
                    podcastId = it.podcastId,
                    videoId = it.videoId,
                    title = it.title,
                    channelName = it.channelName,
                    source = it.source,
                    type = it.type,
                    rank = it.rank,
                    capturedAtTimestamp = it.capturedAtTimestamp,
                    durationSeconds = it.durationSeconds,
                    lastModified = it.lastModified,
                    isSynced = it.isSynced,
                    isDeleted = it.isDeleted,
                    localFilePath = it.localFilePath,
                    downloadStatus = it.downloadStatus,
                )
            }
        }

        is GatekeeperAction.DownloadMediaRequested,
        is GatekeeperAction.DownloadCompleted,
        is GatekeeperAction.DownloadFailed,
        is GatekeeperAction.DeleteDownloadedMedia,
        -> {
            val actionId =
                when (action) {
                    is GatekeeperAction.DownloadMediaRequested -> action.id
                    is GatekeeperAction.DownloadCompleted -> action.id
                    is GatekeeperAction.DownloadFailed -> action.id
                    is GatekeeperAction.DeleteDownloadedMedia -> action.id
                    else -> null
                }
            val item = newState.contentItems.find { it.id == actionId }
            if (item != null) {
                db.contentItemQueries.updateDownloadStatus(
                    downloadStatus = item.downloadStatus,
                    localFilePath = item.localFilePath,
                    lastModified = currentTimeMillis(),
                    id = item.id,
                )
            }
        }

        is GatekeeperAction.ReorderContentBank -> {
            platformLog("Gatekeeper", "🗄️ DB: Reordering Content Bank")
            db.transaction {
                newState.contentItems.forEach { item ->
                    db.contentItemQueries.updateRank(rank = item.rank, lastModified = action.currentTimestamp, id = item.id)
                }
            }
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing ContentItem: ${action.id}")
            db.transaction {
                db.contentItemQueries.delete(lastModified = action.currentTimestamp, id = action.id)
                val slots = db.intentionalSlotQueries.selectAll().executeAsList()
                slots.forEach { slot ->
                    if (slot.id == action.id) {
                        db.intentionalSlotQueries.delete(slot.slotIndex)
                    }
                }
            }
        }

        is GatekeeperAction.LoadPodcastEpisodes -> {
            val cached =
                db.podcastEpisodeQueries.selectAllForPodcast(action.podcastId).executeAsList().map {
                    com.aegisgatekeeper.app.domain.CachedEpisode(
                        id = it.id,
                        podcastId = it.podcastId,
                        title = it.title,
                        audioUrl = it.audioUrl,
                        durationSeconds = it.durationSeconds,
                        pubDate = it.pubDate,
                        lastModified = it.lastModified,
                    )
                }
            if (cached.isNotEmpty()) {
                platformLog("Gatekeeper", "🗄️ DB: Loaded ${cached.size} cached episodes for podcast ${action.podcastId}")
                dispatch(GatekeeperAction.PodcastEpisodesLoaded(cached, action.podcastId))
            }
        }

        is GatekeeperAction.CacheParsedEpisodes -> {
            platformLog("Gatekeeper", "🗄️ DB: Caching ${action.episodes.size} episodes for podcast ${action.podcastId}")
            db.transaction {
                action.episodes.forEachIndexed { index, ep ->
                    val id = randomUUIDString() // Simplified from nameUUIDFromBytes for pure Kotlin
                    db.podcastEpisodeQueries.insertOrReplace(
                        id = id,
                        podcastId = action.podcastId,
                        title = ep.title,
                        audioUrl = ep.audioUrl,
                        durationSeconds = ep.durationSeconds,
                        pubDate = ep.pubDate,
                        lastModified =
                            com.aegisgatekeeper.app.domain
                                .parseRssPubDate(ep.pubDate, 0L - index),
                    )
                }
                db.podcastEpisodeQueries.deleteOldEpisodes(action.podcastId, 200)
            }

            if (newState.activePodcastId == action.podcastId) {
                val cached =
                    db.podcastEpisodeQueries.selectAllForPodcast(action.podcastId).executeAsList().map {
                        com.aegisgatekeeper.app.domain.CachedEpisode(
                            id = it.id,
                            podcastId = it.podcastId,
                            title = it.title,
                            audioUrl = it.audioUrl,
                            durationSeconds = it.durationSeconds,
                            pubDate = it.pubDate,
                            lastModified = it.lastModified,
                        )
                    }
                dispatch(GatekeeperAction.PodcastEpisodesLoaded(cached, action.podcastId))
            }

            if (newState.latestGlobalEpisodes != null || newState.activePodcastId == null) {
                dispatch(GatekeeperAction.LoadLatestGlobalEpisodes)
            }
        }

        GatekeeperAction.LoadLatestGlobalEpisodes -> {
            val episodes =
                db.podcastEpisodeQueries.selectAllLatestGlobal().executeAsList().map { row ->
                    com.aegisgatekeeper.app.domain.UnifiedEpisode(
                        id = row.id,
                        podcastId = row.podcastId,
                        title = row.title,
                        audioUrl = row.audioUrl,
                        durationSeconds = row.durationSeconds,
                        pubDate = row.pubDate,
                        lastModified = row.lastModified,
                        showTitle = row.showTitle,
                        artworkUrl = row.artworkUrl,
                    )
                }
            platformLog("Gatekeeper", "🗄️ DB: Loaded ${episodes.size} latest global episodes")
            dispatch(GatekeeperAction.LatestGlobalEpisodesLoaded(episodes))
        }

        is GatekeeperAction.SaveIntentionalSlot -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting IntentionalSlotItem at slot ${action.slotIndex}")
            db.intentionalSlotQueries.insert(
                slotIndex = action.slotIndex.toLong(),
                contentItemId = action.contentItem.id,
            )
        }

        is GatekeeperAction.ClearIntentionalSlot -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing IntentionalSlotItem at slot ${action.slotIndex}")
            db.intentionalSlotQueries.delete(slotIndex = action.slotIndex.toLong())
        }

        is GatekeeperAction.CreateAppGroup -> {
            db.transaction {
                db.appGroupQueries.insertGroup(action.id, action.name, action.combinator)
                action.apps.forEach { app ->
                    db.appGroupQueries.insertGroupedApp(action.id, app)
                }
            }
        }

        is GatekeeperAction.UpdateGroupCombinator -> {
            db.appGroupQueries.updateCombinator(action.combinator, action.groupId)
        }

        is GatekeeperAction.UpdateGroupName -> {
            db.appGroupQueries.updateName(action.newName, action.groupId)
        }

        is GatekeeperAction.UpdateGroupApps -> {
            db.transaction {
                db.appGroupQueries.deleteAllAppsForGroup(action.groupId)
                action.apps.forEach { app ->
                    db.appGroupQueries.insertGroupedApp(action.groupId, app)
                }
            }
        }

        is GatekeeperAction.DeleteAppGroup -> {
            db.appGroupQueries.deleteGroup(action.groupId)
        }

        is GatekeeperAction.AddAlwaysBlockRule -> {
            db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "ALWAYS_BLOCK", true)
        }

        is GatekeeperAction.AddDomainBlockRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "DOMAIN_BLOCK", true)
                db.domainBlockRuleQueries.insert(action.id, action.domains.joinToString(","))
            }
        }

        is GatekeeperAction.UpdateDomainBlockRule -> {
            db.domainBlockRuleQueries.insert(action.ruleId, action.domains.joinToString(","))
        }

        is GatekeeperAction.AddTimeLimitRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "TIME_LIMIT", true)
                db.blockingRuleQueries.insertTimeLimitRule(action.id, action.timeLimitMinutes.toLong())
            }
        }

        is GatekeeperAction.AddScheduledBlockRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "SCHEDULED", true)
                db.blockingRuleQueries.insertScheduledBlockRule(
                    action.id,
                    action.timeSlots.joinToString(",") { "${it.startTimeMinutes}-${it.endTimeMinutes}" },
                    action.daysOfWeek.joinToString(","),
                )
            }
        }

        is GatekeeperAction.AddCheckInRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "CHECK_IN", true)
                db.blockingRuleQueries.insertCheckInRule(
                    action.id,
                    action.checkInTimesMinutes.joinToString(","),
                    action.durationMinutes.toLong(),
                    action.daysOfWeek.joinToString(","),
                )
            }
        }

        is GatekeeperAction.UpdateCheckInRule -> {
            db.blockingRuleQueries.insertCheckInRule(
                action.id,
                action.checkInTimesMinutes.joinToString(","),
                action.durationMinutes.toLong(),
                action.daysOfWeek.joinToString(","),
            )
        }

        is GatekeeperAction.ResetCheckIns -> {
            platformLog("Gatekeeper", "🗄️ DB: Resetting check-ins for group ${action.groupId}")
            db.blockingRuleQueries.deleteConsumedCheckInsForGroup(action.groupId)
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val log = (newState.consumedCheckIns - oldState.consumedCheckIns.toSet()).firstOrNull()
            if (log != null) {
                db.blockingRuleQueries.insertConsumedCheckIn(log.id, log.groupId, log.timeMinutes.toLong(), log.timestamp)
            }
            if (action.reason != null) {
                db.emergencyBypassLogQueries.insert(
                    id = randomUUIDString(),
                    packageName = "Group: ${action.groupId}",
                    reason = action.reason,
                    timestamp = action.currentTimestamp,
                )
            }
        }

        is GatekeeperAction.DeleteRule -> {
            db.blockingRuleQueries.deleteBlockingRule(action.ruleId)
        }

        is GatekeeperAction.ToggleRule -> {
            db.blockingRuleQueries.updateRuleEnabled(action.isEnabled, action.ruleId)
        }

        GatekeeperAction.UpgradeToProTier -> {
            platformLog("Gatekeeper", "🗄️ DB: User Upgraded to Pro Tier")
            db.appSettingsQueries.updateProStatus(true)
        }

        is GatekeeperAction.SetFrictionGame -> {
            platformLog("Gatekeeper", "🗄️ DB: User Changed Friction Game to ${action.game.name}")
            db.appSettingsQueries.updateFrictionGame(action.game)
        }

        is GatekeeperAction.SetManualLockdown -> {
            platformLog("Gatekeeper", "🗄️ DB: User toggled Manual Lockdown to ${action.isActive}")
            db.appSettingsQueries.updateManualLockdown(action.isActive)
        }

        is GatekeeperAction.UpdatePhaseWindows -> {
            platformLog("Gatekeeper", "🗄️ DB: Updating Phase Windows")
            db.appSettingsQueries.updatePhaseWindows(
                deepWorkStart = action.deepWorkStartMinutes.toLong(),
                deepWorkEnd = action.deepWorkEndMinutes.toLong(),
                gatheringStart = action.gatheringStartMinutes.toLong(),
                gatheringEnd = action.gatheringEndMinutes.toLong(),
            )
        }

        GatekeeperAction.ClearNotificationDigest -> {
            platformLog("Gatekeeper", "🗄️ DB: Clearing Notification Digest")
            db.notificationDigestQueries.deleteAll()
        }

        is GatekeeperAction.NotificationIntercepted -> {
            val newLog = (newState.notificationDigest - oldState.notificationDigest.toSet()).firstOrNull()
            newLog?.let {
                platformLog("Gatekeeper", "🗄️ DB: Inserting new NotificationDigest: ${it.id}")
                db.notificationDigestQueries.insert(
                    id = it.id,
                    packageName = it.packageName,
                    title = it.title,
                    content = it.content,
                    timestamp = it.timestamp,
                )
            }
        }

        is GatekeeperAction.UpdateMissionControlApps -> {
            platformLog("Gatekeeper", "🗄️ DB: Updating Mission Control Apps")
            db.transaction {
                db.missionControlAppQueries.deleteAll()
                action.packageNames.forEachIndexed { index, packageName ->
                    db.missionControlAppQueries.insert(packageName, index.toLong())
                }
            }
        }

        is GatekeeperAction.LogGiveUp -> {
            platformLog("Gatekeeper", "🗄️ DB: Logging Give Up for ${action.packageName}")
            db.giveUpLogQueries.insert(
                id = randomUUIDString(),
                packageName = action.packageName,
                timestamp = action.currentTimestamp,
            )
        }

        GatekeeperAction.GenerateExportData -> {
            platformLog("Gatekeeper", "Generating Export Data")
            val vaultItems =
                db.vaultItemQueries.selectAll().executeAsList().map {
                    com.aegisgatekeeper.app.domain
                        .VaultItem(it.id, it.query, it.capturedAtTimestamp, it.isResolved)
                }
            val sessionLogs =
                db.sessionLogQueries.selectAll().executeAsList().map {
                    com.aegisgatekeeper.app.domain.SessionLog(
                        it.id,
                        it.packageName,
                        it.durationMillis,
                        it.emotion,
                        it.loggedAtTimestamp,
                    )
                }
            val bypasses = db.emergencyBypassLogQueries.selectAll().executeAsList()
            val giveUps = db.giveUpLogQueries.selectAll().executeAsList()

            val markdown =
                com.aegisgatekeeper.app.domain.generateMarkdownReport(
                    vaultItems = vaultItems,
                    sessionLogs = sessionLogs,
                    bypassCount = bypasses.size,
                    giveUpCount = giveUps.size,
                )
            dispatch(GatekeeperAction.ExportDataGenerated(markdown))
        }

        is GatekeeperAction.LogSessionMetacognition -> {
            val newLog = (newState.sessionLogs - oldState.sessionLogs.toSet()).firstOrNull()
            newLog?.let {
                platformLog("Gatekeeper", "🗄️ DB: Inserting new SessionLog: ${it.id}")
                db.sessionLogQueries.insert(
                    id = it.id,
                    packageName = it.packageName,
                    durationMillis = it.durationMillis,
                    emotion = it.emotion,
                    loggedAtTimestamp = it.loggedAtTimestamp,
                )
            }
        }

        is GatekeeperAction.SetCustomInterceptionMessage -> {
            platformLog("Gatekeeper", "🗄️ SetCustomInterceptionMessage: Setting message for ${action.packageName}")
            db.customInterceptionMessageQueries.insert(
                packageName = action.packageName,
                message = action.message,
            )
        }

        is GatekeeperAction.RemoveCustomInterceptionMessage -> {
            platformLog("Gatekeeper", "🗄️ RemoveCustomInterceptionMessage: Removing message for ${action.packageName}")
            db.customInterceptionMessageQueries.delete(action.packageName)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            platformLog("Gatekeeper", "🗄️ EmergencyBypassRequested: Logging bypass for ${action.packageName}")
            db.emergencyBypassLogQueries.insert(
                id = randomUUIDString(),
                packageName = action.packageName,
                reason = action.reason,
                timestamp = action.currentTimestamp,
            )
        }

        is GatekeeperAction.SaveMediaPosition -> {
            db.mediaPositionQueries.insert(action.mediaId, action.positionSeconds.toDouble())
        }

        is GatekeeperAction.AddPinnedWebsite -> {
            platformLog("Gatekeeper", "🗄️ DB: Adding pinned website ${action.label}")
            db.missionControlWebsiteQueries.insert(
                id = action.id,
                label = action.label,
                url = action.url,
                rank = newState.missionControlWebsites.size.toLong(),
            )
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing pinned website ${action.id}")
            db.missionControlWebsiteQueries.delete(id = action.id)
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = (newState.alternativeActivities - oldState.alternativeActivities.toSet()).firstOrNull()
            newActivity?.let {
                platformLog("Gatekeeper", "🗄️ DB: Inserting new AlternativeActivity: ${it.description}")
                db.alternativeActivityQueries.insert(
                    id = it.id,
                    description = it.description,
                    createdAtTimestamp = it.createdAtTimestamp,
                )
            }
        }

        is GatekeeperAction.RemoveAlternativeActivity -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing AlternativeActivity: ${action.id}")
            db.alternativeActivityQueries.delete(action.id)
        }

        is GatekeeperAction.ScheduleMessage -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting ScheduledMessage: ${action.message.id}")
            db.scheduledMessageQueries.insert(
                id = action.message.id,
                beeperRoomId = action.message.beeperRoomId,
                chatName = action.message.chatName,
                messageText = action.message.messageText,
                scheduledTimestamp = action.message.scheduledTimestamp,
                status = action.message.status,
            )
        }

        is GatekeeperAction.CancelScheduledMessage -> {
            platformLog("Gatekeeper", "🗄️ DB: Cancelling ScheduledMessage: ${action.id}")
            db.scheduledMessageQueries.updateStatus(com.aegisgatekeeper.app.domain.MessageStatus.CANCELLED, action.id)
        }

        is GatekeeperAction.MessageDelivered -> {
            platformLog("Gatekeeper", "🗄️ DB: Marking ScheduledMessage Sent: ${action.id}")
            db.scheduledMessageQueries.updateStatus(com.aegisgatekeeper.app.domain.MessageStatus.SENT, action.id)
        }

        is GatekeeperAction.MessageFailed -> {
            platformLog("Gatekeeper", "🗄️ DB: Marking ScheduledMessage Failed: ${action.id}")
            db.scheduledMessageQueries.updateStatus(com.aegisgatekeeper.app.domain.MessageStatus.FAILED, action.id)
        }

        else -> { /* Other actions don't interact directly with DB in this handler */ }
    }
}