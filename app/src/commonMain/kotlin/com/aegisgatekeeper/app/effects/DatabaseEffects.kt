package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperEffect
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.currentTimeMillis
import com.aegisgatekeeper.app.domain.parseRssPubDate
import com.aegisgatekeeper.app.domain.platformLog
import com.aegisgatekeeper.app.domain.randomUUIDString

fun executeDatabaseEffect(
    effect: GatekeeperEffect,
    db: GatekeeperDatabase,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (effect) {
        is GatekeeperEffect.DbLoadInitialState -> {
            platformLog("Gatekeeper", "🗄️ DB: Loading initial state from DB asynchronously...")
            db.appSettingsQueries.insertDefault()

            val groupsFromDb = db.appGroupQueries.selectAllGroups().executeAsList()
            val groupedApps = db.appGroupQueries.selectAllGroupedApps().executeAsList()
            val rulesFromDb = db.blockingRuleQueries.selectAllRules().executeAsList()
            val timeLimits =
                db.blockingRuleQueries
                    .selectAllTimeLimitRules()
                    .executeAsList()
                    .associateBy { it.ruleId }
            val scheduledBlocks =
                db.blockingRuleQueries
                    .selectAllScheduledBlockRules()
                    .executeAsList()
                    .associateBy { it.ruleId }
            val checkInRules =
                db.blockingRuleQueries
                    .selectAllCheckInRules()
                    .executeAsList()
                    .associateBy { it.ruleId }
            val domainBlocks =
                db.domainBlockRuleQueries
                    .selectAll()
                    .executeAsList()
                    .associateBy { it.ruleId }
            val consumedCheckInsFromDb = db.blockingRuleQueries.selectAllConsumedCheckIns().executeAsList()
            val consumedCheckInsList =
                consumedCheckInsFromDb.map {
                    com.aegisgatekeeper.app.domain
                        .ConsumedCheckIn(it.id, it.groupId, it.timeMinutes.toInt(), it.timestamp)
                }

            val appGroupsList =
                groupsFromDb.map { group ->
                    val appsForGroup = groupedApps.filter { it.groupId == group.id }.map { it.packageName }.toSet()
                    val rulesForGroup =
                        rulesFromDb.filter { it.groupId == group.id }.mapNotNull { rule ->
                            when (rule.ruleType) {
                                "TIME_LIMIT" -> {
                                    timeLimits[rule.id]?.let {
                                        com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit(
                                            rule.id,
                                            rule.groupId,
                                            rule.isEnabled,
                                            it.timeLimitMinutes.toInt(),
                                        )
                                    }
                                }

                                "SCHEDULED" -> {
                                    scheduledBlocks[rule.id]?.let { sb ->
                                        com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock(
                                            rule.id,
                                            rule.groupId,
                                            rule.isEnabled,
                                            sb.timeSlots.split(",").filter { it.isNotEmpty() }.map {
                                                val parts = it.split("-")
                                                com.aegisgatekeeper.app.domain
                                                    .TimeSlot(parts[0].toInt(), parts[1].toInt())
                                            },
                                            sb.daysOfWeek
                                                .split(
                                                    ",",
                                                ).filter { it.isNotEmpty() }
                                                .map {
                                                    com.aegisgatekeeper.app.domain.DayOfWeek
                                                        .valueOf(it)
                                                }.toSet(),
                                        )
                                    }
                                }

                                "CHECK_IN" -> {
                                    checkInRules[rule.id]?.let { ci ->
                                        com.aegisgatekeeper.app.domain.BlockingRule.CheckIn(
                                            rule.id,
                                            rule.groupId,
                                            rule.isEnabled,
                                            ci.checkInTimes
                                                .split(",")
                                                .filter { it.isNotEmpty() }
                                                .map { it.toInt() },
                                            ci.durationMinutes.toInt(),
                                            ci.daysOfWeek
                                                .split(
                                                    ",",
                                                ).filter { it.isNotEmpty() }
                                                .map {
                                                    com.aegisgatekeeper.app.domain.DayOfWeek
                                                        .valueOf(it)
                                                }.toSet(),
                                        )
                                    }
                                }

                                "DOMAIN_BLOCK" -> {
                                    domainBlocks[rule.id]?.let { dbBlock ->
                                        com.aegisgatekeeper.app.domain.BlockingRule
                                            .DomainBlock(
                                                rule.id,
                                                rule.groupId,
                                                rule.isEnabled,
                                                dbBlock.domains
                                                    .split(
                                                        ",",
                                                    ).filter {
                                                        it.isNotEmpty()
                                                    }.toSet(),
                                            )
                                    }
                                }

                                "ALWAYS_BLOCK" -> {
                                    com.aegisgatekeeper.app.domain.BlockingRule
                                        .AlwaysBlock(rule.id, rule.groupId, rule.isEnabled)
                                }

                                else -> {
                                    null
                                }
                            }
                        }
                    com.aegisgatekeeper.app.domain
                        .AppGroup(group.id, group.name, appsForGroup, rulesForGroup, group.ruleCombinator)
                }

            val customMessagesFromDb =
                db.customInterceptionMessageQueries.selectAll().executeAsList().associate {
                    it.packageName to
                        it.message
                }
            val alternativeActivitiesFromDb = db.alternativeActivityQueries.selectAll().executeAsList()
            val vaultItemsFromDb = db.vaultItemQueries.selectAll().executeAsList()
            val contentItemsFromDb = db.contentItemQueries.selectAllByRank().executeAsList()
            val sessionLogsFromDb = db.sessionLogQueries.selectAll().executeAsList()
            val slotsFromDb = db.intentionalSlotQueries.selectAll().executeAsList()
            val podcastSubscriptionsFromDb = db.podcastSubscriptionQueries.selectAll().executeAsList()
            val mediaPositionsFromDb =
                db.mediaPositionQueries.selectAll().executeAsList().associate {
                    it.mediaId to
                        it.positionSeconds.toFloat()
                }
            val safeChannels =
                try {
                    db.safeYouTubeChannelQueries
                        .selectAll()
                        .executeAsList()
                        .associate { it.channelId to it.channelName }
                } catch (
                    e: Exception,
                ) {
                    emptyMap()
                }
            val appSettings = db.appSettingsQueries.getSettings().executeAsOneOrNull()
            val pinnedWebsitesFromDb =
                db.missionControlWebsiteQueries.selectAll().executeAsList().map {
                    com.aegisgatekeeper.app.domain
                        .PinnedWebsite(it.id, it.label, it.url)
                }
            val scheduledMessagesFromDb =
                db.scheduledMessageQueries.selectAll().executeAsList().map {
                    com.aegisgatekeeper.app.domain.ScheduledMessage(
                        it.id,
                        it.beeperRoomId,
                        it.chatName,
                        it.messageText,
                        it.scheduledTimestamp,
                        it.status,
                    )
                }
            val missionControlAppsFromDb =
                db.missionControlAppQueries
                    .selectAll()
                    .executeAsList()
                    .map { it.packageName }
            val token =
                com.aegisgatekeeper.app.di.GlobalDI.component.tokenProvider
                    .getToken()

            val loadedState =
                com.aegisgatekeeper.app.domain.GatekeeperState(
                    sync =
                        com.aegisgatekeeper.app.domain.SyncAndIntegrationState(
                            isProTier = appSettings?.isProTier ?: false,
                            isAuthenticated = token != null,
                            jwtToken = token,
                            scheduledMessages = scheduledMessagesFromDb,
                            podcastSubscriptions =
                                podcastSubscriptionsFromDb.map {
                                    com.aegisgatekeeper.app.domain
                                        .PodcastSubscription(it.id, it.feedUrl, it.showTitle, it.artworkUrl)
                                },
                        ),
                    interception =
                        com.aegisgatekeeper.app.domain.InterceptionState(
                            isManualLockdownActive = appSettings?.isManualLockdownActive ?: false,
                            activeFrictionGame = appSettings?.activeFrictionGame ?: com.aegisgatekeeper.app.domain.FrictionGame.GAUNTLET,
                            appGroups = appGroupsList,
                        ),
                    data =
                        com.aegisgatekeeper.app.domain.DataState(
                            deepWorkStartMinutes = appSettings?.deepWorkStartMinutes?.toInt() ?: 540,
                            deepWorkEndMinutes = appSettings?.deepWorkEndMinutes?.toInt() ?: 1020,
                            gatheringStartMinutes = appSettings?.gatheringStartMinutes?.toInt() ?: 1080,
                            gatheringEndMinutes = appSettings?.gatheringEndMinutes?.toInt() ?: 1110,
                            missionControlApps = missionControlAppsFromDb,
                            missionControlWebsites = pinnedWebsitesFromDb,
                            customMessages = customMessagesFromDb,
                            consumedCheckIns = consumedCheckInsList,
                            safeYouTubeChannels = safeChannels,
                            alternativeActivities =
                                alternativeActivitiesFromDb.map {
                                    com.aegisgatekeeper.app.domain
                                        .AlternativeActivity(it.id, it.description, it.createdAtTimestamp)
                                },
                            vaultItems =
                                vaultItemsFromDb.map {
                                    com.aegisgatekeeper.app.domain.VaultItem(
                                        it.id,
                                        it.query,
                                        it.capturedAtTimestamp,
                                        it.isResolved,
                                        it.lastModified,
                                        it.isSynced,
                                        it.isDeleted,
                                    )
                                },
                            contentItems =
                                contentItemsFromDb.map {
                                    com.aegisgatekeeper.app.domain.ContentItem(
                                        it.id,
                                        it.podcastId,
                                        it.videoId,
                                        it.title,
                                        it.channelName,
                                        it.source,
                                        it.type,
                                        it.rank,
                                        it.capturedAtTimestamp,
                                        it.durationSeconds,
                                        it.lastModified,
                                        it.isSynced,
                                        it.isDeleted,
                                        it.localFilePath,
                                        it.downloadStatus,
                                    )
                                },
                            sessionLogs =
                                sessionLogsFromDb.map {
                                    com.aegisgatekeeper.app.domain.SessionLog(
                                        it.id,
                                        it.packageName,
                                        it.durationMillis,
                                        it.emotion,
                                        it.loggedAtTimestamp,
                                    )
                                },
                            intentionalSlots =
                                slotsFromDb.map { slot ->
                                    com.aegisgatekeeper.app.domain.IntentionalSlotItem(
                                        slot.slotIndex.toInt(),
                                        com.aegisgatekeeper.app.domain.ContentItem(
                                            slot.id,
                                            slot.podcastId,
                                            slot.videoId,
                                            slot.title,
                                            slot.channelName,
                                            slot.source,
                                            slot.type,
                                            slot.rank,
                                            slot.capturedAtTimestamp,
                                            slot.durationSeconds,
                                            slot.lastModified,
                                            slot.isSynced,
                                            slot.isDeleted,
                                            slot.localFilePath,
                                            slot.downloadStatus,
                                        ),
                                    )
                                },
                        ),
                    media =
                        com.aegisgatekeeper.app.domain
                            .MediaState(savedMediaPositions = mediaPositionsFromDb),
                )
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .InitialStateLoaded(loadedState),
            )
        }

        is GatekeeperEffect.DbInsertVaultItem -> {
            db.vaultItemQueries.insert(
                effect.item.id,
                effect.item.query,
                effect.item.capturedAtTimestamp,
                effect.item.isResolved,
                effect.item.lastModified,
                effect.item.isSynced,
                effect.item.isDeleted,
            )
        }

        is GatekeeperEffect.DbMarkVaultItemResolved -> {
            db.vaultItemQueries.markAsResolved(effect.lastModified, effect.id)
        }

        is GatekeeperEffect.DbInsertPodcastSubscription -> {
            db.podcastSubscriptionQueries.insert(
                effect.subscription.id,
                effect.subscription.feedUrl,
                effect.subscription.showTitle,
                effect.subscription.artworkUrl,
            )
        }

        is GatekeeperEffect.DbUpsertContentItem -> {
            db.contentItemQueries.insert(
                effect.item.id,
                effect.item.podcastId,
                effect.item.videoId,
                effect.item.title,
                effect.item.channelName,
                effect.item.source,
                effect.item.type,
                effect.item.rank,
                effect.item.capturedAtTimestamp,
                effect.item.durationSeconds,
                effect.item.lastModified,
                effect.item.isSynced,
                effect.item.isDeleted,
                effect.item.localFilePath,
                effect.item.downloadStatus,
            )
        }

        is GatekeeperEffect.DbUpdateDownloadStatus -> {
            db.contentItemQueries.updateDownloadStatus(effect.status, effect.localFilePath, effect.lastModified, effect.id)
        }

        is GatekeeperEffect.DbUpdateRank -> {
            db.contentItemQueries.updateRank(effect.rank, effect.lastModified, effect.id)
        }

        is GatekeeperEffect.DbRemoveFromContentBank -> {
            db.transaction {
                db.contentItemQueries.delete(effect.lastModified, effect.id)
                db.intentionalSlotQueries.selectAll().executeAsList().forEach {
                    if (it.id ==
                        effect.id
                    ) {
                        db.intentionalSlotQueries.delete(it.slotIndex)
                    }
                }
            }
        }

        is GatekeeperEffect.DbCacheParsedEpisodes -> {
            db.transaction {
                effect.episodes.forEachIndexed { i, ep ->
                    db.podcastEpisodeQueries.insertOrReplace(
                        randomUUIDString(),
                        effect.podcastId,
                        ep.title,
                        ep.audioUrl,
                        ep.durationSeconds,
                        ep.pubDate,
                        parseRssPubDate(
                            ep.pubDate,
                            0L - i,
                        ),
                    )
                }
                ; db.podcastEpisodeQueries.deleteOldEpisodes(effect.podcastId, 200)
            }
        }

        is GatekeeperEffect.DbInsertIntentionalSlot -> {
            db.intentionalSlotQueries.insert(effect.slotIndex.toLong(), effect.contentItemId)
        }

        is GatekeeperEffect.DbClearIntentionalSlot -> {
            db.intentionalSlotQueries.delete(effect.slotIndex.toLong())
        }

        is GatekeeperEffect.DbCreateAppGroup -> {
            db.transaction {
                db.appGroupQueries.insertGroup(effect.id, effect.name, effect.combinator)
                effect.apps.forEach { db.appGroupQueries.insertGroupedApp(effect.id, it) }
            }
        }

        is GatekeeperEffect.DbCreateAppGroup -> {
            db.transaction {
                db.appGroupQueries.insertGroup(effect.id, effect.name, effect.combinator)
                effect.apps.forEach { db.appGroupQueries.insertGroupedApp(effect.id, it) }
            }
        }

        is GatekeeperEffect.DbUpdateGroupCombinator -> {
            db.appGroupQueries.updateCombinator(effect.combinator, effect.groupId)
        }

        is GatekeeperEffect.DbUpdateGroupName -> {
            db.appGroupQueries.updateName(effect.newName, effect.groupId)
        }

        is GatekeeperEffect.DbUpdateGroupApps -> {
            db.transaction {
                db.appGroupQueries.deleteAllAppsForGroup(effect.groupId)
                effect.apps.forEach { db.appGroupQueries.insertGroupedApp(effect.groupId, it) }
            }
        }

        is GatekeeperEffect.DbDeleteAppGroup -> {
            db.appGroupQueries.deleteGroup(effect.groupId)
        }

        is GatekeeperEffect.DbAddAlwaysBlockRule -> {
            db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "ALWAYS_BLOCK", true)
        }

        is GatekeeperEffect.DbAddDomainBlockRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "DOMAIN_BLOCK", true)
                db.domainBlockRuleQueries.insert(effect.id, effect.domains.joinToString(","))
            }
        }

        is GatekeeperEffect.DbUpdateDomainBlockRule -> {
            db.domainBlockRuleQueries.insert(effect.ruleId, effect.domains.joinToString(","))
        }

        is GatekeeperEffect.DbAddTimeLimitRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "TIME_LIMIT", true)
                db.blockingRuleQueries.insertTimeLimitRule(effect.id, effect.timeLimitMinutes.toLong())
            }
        }

        is GatekeeperEffect.DbAddScheduledBlockRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "SCHEDULED", true)
                db.blockingRuleQueries.insertScheduledBlockRule(
                    effect.id,
                    effect.timeSlots.joinToString(",") {
                        "${it.startTimeMinutes}-${it.endTimeMinutes}"
                    },
                    effect.daysOfWeek.joinToString(",") { it.name },
                )
            }
        }

        is GatekeeperEffect.DbAddCheckInRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "CHECK_IN", true)
                db.blockingRuleQueries.insertCheckInRule(
                    effect.id,
                    effect.checkInTimesMinutes.joinToString(
                        ",",
                    ),
                    effect.durationMinutes.toLong(),
                    effect.daysOfWeek.joinToString(",") {
                        it.name
                    },
                )
            }
        }

        is GatekeeperEffect.DbUpdateCheckInRule -> {
            db.blockingRuleQueries.insertCheckInRule(
                effect.id,
                effect.checkInTimesMinutes.joinToString(
                    ",",
                ),
                effect.durationMinutes.toLong(),
                effect.daysOfWeek.joinToString(",") {
                    it.name
                },
            )
        }

        is GatekeeperEffect.DbResetCheckIns -> {
            db.blockingRuleQueries.deleteConsumedCheckInsForGroup(effect.groupId)
        }

        is GatekeeperEffect.DbRedeemCheckInToken -> {
            if (effect.log !=
                null
            ) {
                db.blockingRuleQueries.insertConsumedCheckIn(
                    effect.log.id,
                    effect.log.groupId,
                    effect.log.timeMinutes.toLong(),
                    effect.log.timestamp,
                )
            }
        }

        is GatekeeperEffect.DbToggleSafeYouTubeChannel -> {
            if (effect.isSafe) {
                db.safeYouTubeChannelQueries.insert(
                    effect.channelId,
                    effect.channelName,
                )
            } else {
                db.safeYouTubeChannelQueries.delete(effect.channelId)
            }
        }

        is GatekeeperEffect.DbLoadCachedPodcastEpisodes -> {
            val episodes =
                db.podcastEpisodeQueries.selectAllForPodcast(effect.podcastId).executeAsList().map {
                    com.aegisgatekeeper.app.domain.CachedEpisode(
                        it.id,
                        it.podcastId,
                        it.title,
                        it.audioUrl,
                        it.durationSeconds,
                        it.pubDate,
                        it.lastModified,
                    )
                }
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .PodcastEpisodesLoaded(episodes, effect.podcastId),
            )
        }

        is GatekeeperEffect.DbLoadLatestGlobalEpisodes -> {
            val unifiedEpisodes =
                db.podcastEpisodeQueries.selectAllLatestGlobal().executeAsList().map {
                    com.aegisgatekeeper.app.domain.UnifiedEpisode(
                        it.id,
                        it.podcastId,
                        it.title,
                        it.audioUrl,
                        it.durationSeconds,
                        it.pubDate,
                        it.lastModified,
                        it.showTitle,
                        it.artworkUrl,
                    )
                }
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .LatestGlobalEpisodesLoaded(unifiedEpisodes),
            )
        }

        is GatekeeperEffect.DbDeleteRule -> {
            db.blockingRuleQueries.deleteBlockingRule(effect.ruleId)
        }

        is GatekeeperEffect.DbToggleRule -> {
            db.blockingRuleQueries.updateRuleEnabled(effect.isEnabled, effect.ruleId)
        }

        is GatekeeperEffect.DbUpdateProStatus -> {
            db.appSettingsQueries.updateProStatus(effect.isProTier)
        }

        is GatekeeperEffect.DbSetFrictionGame -> {
            db.appSettingsQueries.updateFrictionGame(effect.game)
        }

        is GatekeeperEffect.DbSetManualLockdown -> {
            db.appSettingsQueries.updateManualLockdown(effect.isActive)
        }

        is GatekeeperEffect.DbUpdatePhaseWindows -> {
            db.appSettingsQueries.updatePhaseWindows(
                effect.deepWorkStartMinutes.toLong(),
                effect.deepWorkEndMinutes.toLong(),
                effect.gatheringStartMinutes.toLong(),
                effect.gatheringEndMinutes.toLong(),
            )
        }

        is GatekeeperEffect.DbInsertNotificationDigest -> {
            db.notificationDigestQueries.insert(
                effect.log.id,
                effect.log.packageName,
                effect.log.title,
                effect.log.content,
                effect.log.timestamp,
            )
        }

        is GatekeeperEffect.DbUpdateMissionControlApps -> {
            db.transaction {
                db.missionControlAppQueries.deleteAll()
                effect.packageNames.forEachIndexed { i, p -> db.missionControlAppQueries.insert(p, i.toLong()) }
            }
        }

        is GatekeeperEffect.DbLogGiveUp -> {
            db.giveUpLogQueries.insert(randomUUIDString(), effect.packageName, effect.timestamp)
        }

        is GatekeeperEffect.DbInsertSessionLog -> {
            db.sessionLogQueries.insert(
                effect.log.id,
                effect.log.packageName,
                effect.log.durationMillis,
                effect.log.emotion,
                effect.log.loggedAtTimestamp,
            )
        }

        is GatekeeperEffect.DbSetCustomInterceptionMessage -> {
            db.customInterceptionMessageQueries.insert(effect.packageName, effect.message)
        }

        is GatekeeperEffect.DbLogEmergencyBypass -> {
            db.emergencyBypassLogQueries.insert(randomUUIDString(), effect.packageName, effect.reason, effect.timestamp)
        }

        is GatekeeperEffect.DbSaveMediaPosition -> {
            db.mediaPositionQueries.insert(effect.mediaId, effect.positionSeconds.toDouble())
        }

        is GatekeeperEffect.DbAddPinnedWebsite -> {
            db.missionControlWebsiteQueries.insert(effect.website.id, effect.website.label, effect.website.url, effect.rank)
        }

        is GatekeeperEffect.DbRemovePinnedWebsite -> {
            db.missionControlWebsiteQueries.delete(effect.id)
        }

        is GatekeeperEffect.DbAddAlternativeActivity -> {
            db.alternativeActivityQueries.insert(effect.activity.id, effect.activity.description, effect.activity.createdAtTimestamp)
        }

        is GatekeeperEffect.DbRemoveAlternativeActivity -> {
            db.alternativeActivityQueries.delete(effect.id)
        }

        is GatekeeperEffect.DbInsertScheduledMessage -> {
            db.scheduledMessageQueries.insert(
                effect.message.id,
                effect.message.beeperRoomId,
                effect.message.chatName,
                effect.message.messageText,
                effect.message.scheduledTimestamp,
                effect.message.status,
            )
        }

        is GatekeeperEffect.DbUpdateScheduledMessageStatus -> {
            db.scheduledMessageQueries.updateStatus(effect.status, effect.id)
        }

        is GatekeeperEffect.DbRemoteSyncCompleted -> {
            db.transaction {
                effect.newVaultItems.forEach {
                    db.vaultItemQueries.insert(it.id, it.query, it.capturedAtTimestamp, it.isResolved, it.lastModified, true, it.isDeleted)
                }
                effect.newContentItems.forEach {
                    db.contentItemQueries.insert(
                        it.id,
                        it.podcastId,
                        it.videoId,
                        it.title,
                        it.channelName,
                        it.source,
                        it.type,
                        it.rank,
                        it.capturedAtTimestamp,
                        it.durationSeconds,
                        it.lastModified,
                        true,
                        it.isDeleted,
                        it.localFilePath,
                        it.downloadStatus,
                    )
                }
            }
        }

        else -> {}
    }
}
