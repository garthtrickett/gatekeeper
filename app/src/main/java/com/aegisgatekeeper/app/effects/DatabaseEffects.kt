package com.aegisgatekeeper.app.effects

import android.util.Log
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import java.util.UUID

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
                Log.i("Gatekeeper", "DB: Inserting new VaultItem: ${it.id}")
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
            Log.i("Gatekeeper", "DB: Marking VaultItem as resolved: ${action.id}")
            db.vaultItemQueries.markAsResolved(
                lastModified = action.currentTimestamp,
                id = action.id,
            )
        }

        is GatekeeperAction.SaveToContentBank -> {
            val updatedOrNewItem = newState.contentItems.find { it.videoId == action.videoId && it.source == action.source }
            updatedOrNewItem?.let {
                Log.i("Gatekeeper", "DB: Upserting ContentItem: ${it.title}")
                db.contentItemQueries.insert(
                    id = it.id,
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
                )
            }
        }

        is GatekeeperAction.ReorderContentBank -> {
            Log.i("Gatekeeper", "DB: Reordering Content Bank")
            db.transaction {
                newState.contentItems.forEach { item ->
                    db.contentItemQueries.updateRank(rank = item.rank, lastModified = action.currentTimestamp, id = item.id)
                }
            }
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            Log.i("Gatekeeper", "DB: Removing ContentItem: ${action.id}")
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

        is GatekeeperAction.SaveIntentionalSlot -> {
            Log.i("Gatekeeper", "DB: Inserting IntentionalSlotItem at slot ${action.slotIndex}")
            db.intentionalSlotQueries.insert(
                slotIndex = action.slotIndex.toLong(),
                contentItemId = action.contentItem.id,
            )
        }

        is GatekeeperAction.ClearIntentionalSlot -> {
            Log.i("Gatekeeper", "DB: Removing IntentionalSlotItem at slot ${action.slotIndex}")
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

        is GatekeeperAction.RedeemCheckInToken -> {
            val log = (newState.consumedCheckIns - oldState.consumedCheckIns.toSet()).firstOrNull()
            if (log != null) {
                db.blockingRuleQueries.insertConsumedCheckIn(log.id, log.groupId, log.timeMinutes.toLong(), log.timestamp)
            }
            if (action.reason != null) {
                db.emergencyBypassLogQueries.insert(
                    id = UUID.randomUUID().toString(),
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
            Log.i("Gatekeeper", "DB: User Upgraded to Pro Tier")
            db.appSettingsQueries.updateProStatus(true)
        }

        is GatekeeperAction.SetFrictionGame -> {
            Log.i("Gatekeeper", "DB: User Changed Friction Game to ${action.game.name}")
            db.appSettingsQueries.updateFrictionGame(action.game)
        }

        is GatekeeperAction.SetManualLockdown -> {
            Log.i("Gatekeeper", "DB: User toggled Manual Lockdown to ${action.isActive}")
            db.appSettingsQueries.updateManualLockdown(action.isActive)
        }

        GatekeeperAction.ClearNotificationDigest -> {
            Log.i("Gatekeeper", "DB: Clearing Notification Digest")
            db.notificationDigestQueries.deleteAll()
        }

        is GatekeeperAction.UpdateMissionControlApps -> {
            Log.i("Gatekeeper", "DB: Updating Mission Control Apps")
            db.transaction {
                db.missionControlAppQueries.deleteAll()
                action.packageNames.forEachIndexed { index, packageName ->
                    db.missionControlAppQueries.insert(packageName, index.toLong())
                }
            }
        }

        is GatekeeperAction.LogGiveUp -> {
            Log.i("Gatekeeper", "DB: Logging Give Up for ${action.packageName}")
            db.giveUpLogQueries.insert(
                id = UUID.randomUUID().toString(),
                packageName = action.packageName,
                timestamp = action.currentTimestamp,
            )
        }

        GatekeeperAction.GenerateExportData -> {
            Log.i("Gatekeeper", "Generating Export Data")
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
                Log.i("Gatekeeper", "DB: Inserting new SessionLog: ${it.id}")
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
            Log.d("Gatekeeper", "🗄️ SetCustomInterceptionMessage: Setting message for ${action.packageName}")
            db.customInterceptionMessageQueries.insert(
                packageName = action.packageName,
                message = action.message,
            )
        }

        is GatekeeperAction.RemoveCustomInterceptionMessage -> {
            Log.d("Gatekeeper", "🗄️ RemoveCustomInterceptionMessage: Removing message for ${action.packageName}")
            db.customInterceptionMessageQueries.delete(action.packageName)
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            Log.d("Gatekeeper", "🗄️ EmergencyBypassRequested: Logging bypass for ${action.packageName}")
            db.emergencyBypassLogQueries.insert(
                id = UUID.randomUUID().toString(),
                packageName = action.packageName,
                reason = action.reason,
                timestamp = action.currentTimestamp,
            )
        }

        is GatekeeperAction.SaveMediaPosition -> {
            db.mediaPositionQueries.insert(action.mediaId, action.positionSeconds.toDouble())
        }

        is GatekeeperAction.AddPinnedWebsite -> {
            Log.i("Gatekeeper", "DB: Adding pinned website ${action.label}")
            db.missionControlWebsiteQueries.insert(
                id = action.id,
                label = action.label,
                url = action.url,
                rank = newState.missionControlWebsites.size.toLong(),
            )
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            Log.i("Gatekeeper", "DB: Removing pinned website ${action.id}")
            db.missionControlWebsiteQueries.delete(id = action.id)
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = (newState.alternativeActivities - oldState.alternativeActivities.toSet()).firstOrNull()
            newActivity?.let {
                Log.i("Gatekeeper", "DB: Inserting new AlternativeActivity: ${it.description}")
                db.alternativeActivityQueries.insert(
                    id = it.id,
                    description = it.description,
                    createdAtTimestamp = it.createdAtTimestamp,
                )
            }
        }

        is GatekeeperAction.RemoveAlternativeActivity -> {
            Log.i("Gatekeeper", "DB: Removing AlternativeActivity: ${action.id}")
            db.alternativeActivityQueries.delete(action.id)
        }

        else -> { /* Other actions don't interact directly with DB in this handler */ }
    }
}
