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

            if (db.appGroupQueries
                    .selectAllGroups()
                    .executeAsList()
                    .isEmpty()
            ) {
                platformLog("Gatekeeper", "🗄️ DB: Seeding initial app groups...")
                val initialApps = setOf("com.android.chrome", "org.mozilla.firefox", "com.instagram.android")
                val groupId =
                    com.aegisgatekeeper.app.domain
                        .randomUUIDString()
                db.transaction {
                    db.appGroupQueries.insertGroup(groupId, "Distractions", com.aegisgatekeeper.app.domain.RuleCombinator.ANY)
                    initialApps.forEach { packageName ->
                        db.appGroupQueries.insertGroupedApp(groupId, packageName)
                    }
                }
            }

            db.transaction {
                val allContent = db.contentItemQueries.selectAllByRank().executeAsList()
                val allSlots = db.intentionalSlotQueries.selectAll().executeAsList()

                allSlots.forEach { slot ->
                    val freshest =
                        allContent
                            .filter { it.videoId == slot.videoId && it.isDeleted != true }
                            .maxByOrNull { it.lastModified }

                    if (freshest != null && freshest.id != slot.id) {
                        db.intentionalSlotQueries.insert(slot.slotIndex, freshest.id)
                    } else if (slot.isDeleted == true || freshest == null) {
                        db.intentionalSlotQueries.delete(slot.slotIndex)
                    }
                }
            }

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
                                    timeLimits[rule.id]?.let { tl ->
                                        com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit(
                                            id = rule.id,
                                            groupId = rule.groupId,
                                            isEnabled = rule.isEnabled,
                                            timeLimitMinutes = tl.timeLimitMinutes.toInt(),
                                        )
                                    }
                                }

                                "SCHEDULED" -> {
                                    scheduledBlocks[rule.id]?.let { sb ->
                                        com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock(
                                            id = rule.id,
                                            groupId = rule.groupId,
                                            isEnabled = rule.isEnabled,
                                            timeSlots =
                                                sb.timeSlots.split(",").filter { it.isNotEmpty() }.map {
                                                    val parts = it.split("-")
                                                    com.aegisgatekeeper.app.domain
                                                        .TimeSlot(parts[0].toInt(), parts[1].toInt())
                                                },
                                            daysOfWeek =
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
                                        com.aegisgatekeeper.app.domain.BlockingRule
                                            .CheckIn(
                                                id = rule.id,
                                                groupId = rule.groupId,
                                                isEnabled = rule.isEnabled,
                                                checkInTimesMinutes =
                                                    ci.checkInTimes
                                                        .split(
                                                            ",",
                                                        ).filter {
                                                            it.isNotEmpty()
                                                        }.map {
                                                            it.toInt()
                                                        },
                                                durationMinutes = ci.durationMinutes.toInt(),
                                                daysOfWeek =
                                                    ci.daysOfWeek
                                                        .split(",")
                                                        .filter {
                                                            it.isNotEmpty()
                                                        }.map {
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
                                                id = rule.id,
                                                groupId = rule.groupId,
                                                isEnabled = rule.isEnabled,
                                                domains =
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
                                    com.aegisgatekeeper.app.domain.BlockingRule.AlwaysBlock(
                                        id = rule.id,
                                        groupId = rule.groupId,
                                        isEnabled = rule.isEnabled,
                                    )
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
            val safeChannels = db.safeYouTubeChannelQueries.selectAll().executeAsList().associate { it.channelId to it.channelName }
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
                                    com.aegisgatekeeper.app.domain.PodcastSubscription(
                                        id = it.id,
                                        feedUrl = it.feedUrl,
                                        showTitle = it.showTitle,
                                        artworkUrl = it.artworkUrl,
                                        lastModified =
                                            com.aegisgatekeeper.app.domain
                                                .currentTimeMillis(),
                                        isSynced = false,
                                        isDeleted = false,
                                    )
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
                                },
                            sessionLogs =
                                sessionLogsFromDb.map {
                                    com.aegisgatekeeper.app.domain
                                        .SessionLog(it.id, it.packageName, it.durationMillis, it.emotion, it.loggedAtTimestamp)
                                },
                            intentionalSlots =
                                slotsFromDb.map { slot ->
                                    com.aegisgatekeeper.app.domain.IntentionalSlotItem(
                                        slotIndex = slot.slotIndex.toInt(),
                                        contentItem =
                                            com.aegisgatekeeper.app.domain.ContentItem(
                                                id = slot.id,
                                                podcastId = slot.podcastId,
                                                videoId = slot.videoId,
                                                title = slot.title,
                                                channelName = slot.channelName,
                                                source = slot.source,
                                                type = slot.type,
                                                rank = slot.rank,
                                                capturedAtTimestamp = slot.capturedAtTimestamp,
                                                durationSeconds = slot.durationSeconds,
                                                lastModified = slot.lastModified,
                                                isSynced = slot.isSynced,
                                                isDeleted = slot.isDeleted,
                                                localFilePath = slot.localFilePath,
                                                downloadStatus = slot.downloadStatus,
                                            ),
                                    )
                                },
                        ),
                    media =
                        com.aegisgatekeeper.app.domain.MediaState(
                            savedMediaPositions = mediaPositionsFromDb,
                        ),
                )
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .InitialStateLoaded(loadedState),
            )
        }

        is GatekeeperEffect.DbInsertVaultItem -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting new VaultItem: ${effect.item.id}")
            db.vaultItemQueries.insert(
                id = effect.item.id,
                query = effect.item.query,
                capturedAtTimestamp = effect.item.capturedAtTimestamp,
                isResolved = effect.item.isResolved,
                lastModified = effect.item.lastModified,
                isSynced = effect.item.isSynced,
                isDeleted = effect.item.isDeleted,
            )
        }

        is GatekeeperEffect.DbMarkVaultItemResolved -> {
            platformLog("Gatekeeper", "🗄️ DB: Marking VaultItem as resolved: ${effect.id}")
            db.vaultItemQueries.markAsResolved(
                lastModified = effect.lastModified,
                id = effect.id,
            )
        }

        is GatekeeperEffect.DbInsertPodcastSubscription -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting PodcastSubscription: ${effect.subscription.showTitle}")
            db.podcastSubscriptionQueries.insert(
                id = effect.subscription.id,
                feedUrl = effect.subscription.feedUrl,
                showTitle = effect.subscription.showTitle,
                artworkUrl = effect.subscription.artworkUrl,
            )
        }

        is GatekeeperEffect.DbDeletePodcastSubscription -> {
            platformLog("Gatekeeper", "🗄️ DB: Deleting PodcastSubscription: ${effect.id}")
            db.transaction {
                db.podcastSubscriptionQueries.delete(effect.id)
                val itemsToDelete =
                    db.contentItemQueries
                        .selectAllByRank()
                        .executeAsList()
                        .filter { it.podcastId == effect.id }
                val slots = db.intentionalSlotQueries.selectAll().executeAsList()
                itemsToDelete.forEach { item ->
                    db.contentItemQueries.delete(lastModified = effect.lastModified, id = item.id)
                    slots.forEach { slot ->
                        if (slot.id == item.id) {
                            db.intentionalSlotQueries.delete(slot.slotIndex)
                        }
                    }
                }
            }
        }

        is GatekeeperEffect.DbUpsertContentItem -> {
            platformLog("Gatekeeper", "🗄️ DB: Upserting ContentItem: ${effect.item.title}")
            db.contentItemQueries.insert(
                id = effect.item.id,
                podcastId = effect.item.podcastId,
                videoId = effect.item.videoId,
                title = effect.item.title,
                channelName = effect.item.channelName,
                source = effect.item.source,
                type = effect.item.type,
                rank = effect.item.rank,
                capturedAtTimestamp = effect.item.capturedAtTimestamp,
                durationSeconds = effect.item.durationSeconds,
                lastModified = effect.item.lastModified,
                isSynced = effect.item.isSynced,
                isDeleted = effect.item.isDeleted,
                localFilePath = effect.item.localFilePath,
                downloadStatus = effect.item.downloadStatus,
            )
        }

        is GatekeeperEffect.DbUpdateDownloadStatus -> {
            db.contentItemQueries.updateDownloadStatus(
                downloadStatus = effect.status,
                localFilePath = effect.localFilePath,
                lastModified = effect.lastModified,
                id = effect.id,
            )
        }

        is GatekeeperEffect.DbUpdateRank -> {
            db.contentItemQueries.updateRank(rank = effect.rank, lastModified = effect.lastModified, id = effect.id)
        }

        is GatekeeperEffect.DbRemoveFromContentBank -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing ContentItem: ${effect.id}")
            db.transaction {
                db.contentItemQueries.delete(lastModified = effect.lastModified, id = effect.id)
                val slots = db.intentionalSlotQueries.selectAll().executeAsList()
                slots.forEach { slot ->
                    if (slot.id == effect.id) {
                        db.intentionalSlotQueries.delete(slot.slotIndex)
                    }
                }
            }
        }

        is GatekeeperEffect.DbLoadCachedPodcastEpisodes -> {
            val cached =
                db.podcastEpisodeQueries.selectAllForPodcast(effect.podcastId).executeAsList().map {
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
                platformLog("Gatekeeper", "🗄️ DB: Loaded ${cached.size} cached episodes for podcast ${effect.podcastId}")
                dispatch(
                    com.aegisgatekeeper.app.domain.GatekeeperAction
                        .PodcastEpisodesLoaded(cached, effect.podcastId),
                )
            }
        }

        is GatekeeperEffect.DbCacheParsedEpisodes -> {
            platformLog("Gatekeeper", "🗄️ DB: Caching ${effect.episodes.size} episodes for podcast ${effect.podcastId}")
            db.transaction {
                effect.episodes.forEachIndexed { index, ep ->
                    val id =
                        com.aegisgatekeeper.app.domain
                            .randomUUIDString()
                    db.podcastEpisodeQueries.insertOrReplace(
                        id = id,
                        podcastId = effect.podcastId,
                        title = ep.title,
                        audioUrl = ep.audioUrl,
                        durationSeconds = ep.durationSeconds,
                        pubDate = ep.pubDate,
                        lastModified =
                            com.aegisgatekeeper.app.domain
                                .parseRssPubDate(ep.pubDate, 0L - index),
                    )
                }
                db.podcastEpisodeQueries.deleteOldEpisodes(effect.podcastId, 200)
            }
            val cached =
                db.podcastEpisodeQueries.selectAllForPodcast(effect.podcastId).executeAsList().map {
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
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .PodcastEpisodesLoaded(cached, effect.podcastId),
            )
        }

        is GatekeeperEffect.DbLoadLatestGlobalEpisodes -> {
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
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .LatestGlobalEpisodesLoaded(episodes),
            )
        }

        is GatekeeperEffect.DbInsertIntentionalSlot -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting IntentionalSlotItem at slot ${effect.slotIndex}")
            db.intentionalSlotQueries.insert(
                slotIndex = effect.slotIndex.toLong(),
                contentItemId = effect.contentItemId,
            )
        }

        is GatekeeperEffect.DbClearIntentionalSlot -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing IntentionalSlotItem at slot ${effect.slotIndex}")
            db.intentionalSlotQueries.delete(slotIndex = effect.slotIndex.toLong())
        }

        is GatekeeperEffect.DbCreateAppGroup -> {
            db.transaction {
                db.appGroupQueries.insertGroup(effect.id, effect.name, effect.combinator)
                effect.apps.forEach { app ->
                    db.appGroupQueries.insertGroupedApp(effect.id, app)
                }
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
                effect.apps.forEach { app ->
                    db.appGroupQueries.insertGroupedApp(effect.groupId, app)
                }
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
                    effect.timeSlots.joinToString(",") { "${it.startTimeMinutes}-${it.endTimeMinutes}" },
                    effect.daysOfWeek.joinToString(","),
                )
            }
        }

        is GatekeeperEffect.DbAddCheckInRule -> {
            db.transaction {
                db.blockingRuleQueries.insertBlockingRule(effect.id, effect.groupId, "CHECK_IN", true)
                db.blockingRuleQueries.insertCheckInRule(
                    effect.id,
                    effect.checkInTimesMinutes.joinToString(","),
                    effect.durationMinutes.toLong(),
                    effect.daysOfWeek.joinToString(","),
                )
            }
        }

        is GatekeeperEffect.DbUpdateCheckInRule -> {
            db.blockingRuleQueries.insertCheckInRule(
                effect.id,
                effect.checkInTimesMinutes.joinToString(","),
                effect.durationMinutes.toLong(),
                effect.daysOfWeek.joinToString(","),
            )
        }

        is GatekeeperEffect.DbResetCheckIns -> {
            platformLog("Gatekeeper", "🗄️ DB: Resetting check-ins for group ${effect.groupId}")
            db.blockingRuleQueries.deleteConsumedCheckInsForGroup(effect.groupId)
        }

        is GatekeeperEffect.DbRedeemCheckInToken -> {
            if (effect.log != null) {
                db.blockingRuleQueries.insertConsumedCheckIn(
                    effect.log.id,
                    effect.log.groupId,
                    effect.log.timeMinutes.toLong(),
                    effect.log.timestamp,
                )
            }
            if (effect.reason != null) {
                db.emergencyBypassLogQueries.insert(
                    id =
                        com.aegisgatekeeper.app.domain
                            .randomUUIDString(),
                    packageName = "Group: ${effect.groupId}",
                    reason = effect.reason,
                    timestamp = effect.timestamp,
                )
            }
        }

        is GatekeeperEffect.DbToggleSafeYouTubeChannel -> {
            if (effect.isSafe) {
                db.safeYouTubeChannelQueries.insert(effect.channelId, effect.channelName)
            } else {
                db.safeYouTubeChannelQueries.delete(effect.channelId)
            }
        }

        is GatekeeperEffect.DbDeleteRule -> {
            db.blockingRuleQueries.deleteBlockingRule(effect.ruleId)
        }

        is GatekeeperEffect.DbToggleRule -> {
            db.blockingRuleQueries.updateRuleEnabled(effect.isEnabled, effect.ruleId)
        }

        is GatekeeperEffect.DbUpdateProStatus -> {
            platformLog("Gatekeeper", "🗄️ DB: User Upgraded to Pro Tier")
            db.appSettingsQueries.updateProStatus(effect.isProTier)
        }

        is GatekeeperEffect.DbSetFrictionGame -> {
            platformLog("Gatekeeper", "🗄️ DB: User Changed Friction Game to ${effect.game.name}")
            db.appSettingsQueries.updateFrictionGame(effect.game)
        }

        is GatekeeperEffect.DbSetManualLockdown -> {
            platformLog("Gatekeeper", "🗄️ DB: User toggled Manual Lockdown to ${effect.isActive}")
            db.appSettingsQueries.updateManualLockdown(effect.isActive)
        }

        is GatekeeperEffect.DbUpdatePhaseWindows -> {
            platformLog("Gatekeeper", "🗄️ DB: Updating Phase Windows")
            db.appSettingsQueries.updatePhaseWindows(
                deepWorkStart = effect.deepWorkStartMinutes.toLong(),
                deepWorkEnd = effect.deepWorkEndMinutes.toLong(),
                gatheringStart = effect.gatheringStartMinutes.toLong(),
                gatheringEnd = effect.gatheringEndMinutes.toLong(),
            )
        }

        is GatekeeperEffect.DbClearNotificationDigest -> {
            platformLog("Gatekeeper", "🗄️ DB: Clearing Notification Digest")
            db.notificationDigestQueries.deleteAll()
        }

        is GatekeeperEffect.DbInsertNotificationDigest -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting new NotificationDigest: ${effect.log.id}")
            db.notificationDigestQueries.insert(
                id = effect.log.id,
                packageName = effect.log.packageName,
                title = effect.log.title,
                content = effect.log.content,
                timestamp = effect.log.timestamp,
            )
        }

        is GatekeeperEffect.DbUpdateMissionControlApps -> {
            platformLog("Gatekeeper", "🗄️ DB: Updating Mission Control Apps")
            db.transaction {
                db.missionControlAppQueries.deleteAll()
                effect.packageNames.forEachIndexed { index, packageName ->
                    db.missionControlAppQueries.insert(packageName, index.toLong())
                }
            }
        }

        is GatekeeperEffect.DbLogGiveUp -> {
            platformLog("Gatekeeper", "🗄️ DB: Logging Give Up for ${effect.packageName}")
            db.giveUpLogQueries.insert(
                id =
                    com.aegisgatekeeper.app.domain
                        .randomUUIDString(),
                packageName = effect.packageName,
                timestamp = effect.timestamp,
            )
        }

        is GatekeeperEffect.GenerateExportData -> {
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
            dispatch(
                com.aegisgatekeeper.app.domain.GatekeeperAction
                    .ExportDataGenerated(markdown),
            )
        }

        is GatekeeperEffect.DbInsertSessionLog -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting new SessionLog: ${effect.log.id}")
            db.sessionLogQueries.insert(
                id = effect.log.id,
                packageName = effect.log.packageName,
                durationMillis = effect.log.durationMillis,
                emotion = effect.log.emotion,
                loggedAtTimestamp = effect.log.loggedAtTimestamp,
            )
        }

        is GatekeeperEffect.DbSetCustomInterceptionMessage -> {
            platformLog("Gatekeeper", "🗄️ DbSetCustomInterceptionMessage: Setting message for ${effect.packageName}")
            db.customInterceptionMessageQueries.insert(
                packageName = effect.packageName,
                message = effect.message,
            )
        }

        is GatekeeperEffect.DbRemoveCustomInterceptionMessage -> {
            platformLog("Gatekeeper", "🗄️ DbRemoveCustomInterceptionMessage: Removing message for ${effect.packageName}")
            db.customInterceptionMessageQueries.delete(effect.packageName)
        }

        is GatekeeperEffect.DbLogEmergencyBypass -> {
            platformLog("Gatekeeper", "🗄️ DbLogEmergencyBypass: Logging bypass for ${effect.packageName}")
            db.emergencyBypassLogQueries.insert(
                id =
                    com.aegisgatekeeper.app.domain
                        .randomUUIDString(),
                packageName = effect.packageName,
                reason = effect.reason,
                timestamp = effect.timestamp,
            )
        }

        is GatekeeperEffect.DbSaveMediaPosition -> {
            db.mediaPositionQueries.insert(effect.mediaId, effect.positionSeconds.toDouble())
        }

        is GatekeeperEffect.DbAddPinnedWebsite -> {
            platformLog("Gatekeeper", "🗄️ DB: Adding pinned website ${effect.website.label}")
            db.missionControlWebsiteQueries.insert(
                id = effect.website.id,
                label = effect.website.label,
                url = effect.website.url,
                rank = effect.rank,
            )
        }

        is GatekeeperEffect.DbRemovePinnedWebsite -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing pinned website ${effect.id}")
            db.missionControlWebsiteQueries.delete(id = effect.id)
        }

        is GatekeeperEffect.DbAddAlternativeActivity -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting new AlternativeActivity: ${effect.activity.description}")
            db.alternativeActivityQueries.insert(
                id = effect.activity.id,
                description = effect.activity.description,
                createdAtTimestamp = effect.activity.createdAtTimestamp,
            )
        }

        is GatekeeperEffect.DbRemoveAlternativeActivity -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing AlternativeActivity: ${effect.id}")
            db.alternativeActivityQueries.delete(effect.id)
        }

        is GatekeeperEffect.DbInsertScheduledMessage -> {
            platformLog("Gatekeeper", "🗄️ DB: Inserting ScheduledMessage: ${effect.message.id}")
            db.scheduledMessageQueries.insert(
                id = effect.message.id,
                beeperRoomId = effect.message.beeperRoomId,
                chatName = effect.message.chatName,
                messageText = effect.message.messageText,
                scheduledTimestamp = effect.message.scheduledTimestamp,
                status = effect.message.status,
            )
        }

        is GatekeeperEffect.DbUpdateScheduledMessageStatus -> {
            platformLog("Gatekeeper", "🗄️ DB: Updating ScheduledMessage Status to ${effect.status}: ${effect.id}")
            db.scheduledMessageQueries.updateStatus(effect.status, effect.id)
        }

        is GatekeeperEffect.DbRemoteSyncCompleted -> {
            platformLog("Gatekeeper", "🗄️ DB: Upserting remotely synced items.")
            db.transaction {
                effect.newVaultItems.forEach {
                    db.vaultItemQueries.insert(
                        id = it.id,
                        query = it.query,
                        capturedAtTimestamp = it.capturedAtTimestamp,
                        isResolved = it.isResolved,
                        lastModified = it.lastModified,
                        isSynced = true, // Mark as synced
                        isDeleted = it.isDeleted,
                    )
                }
                effect.newContentItems.forEach {
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
                        isSynced = true, // Mark as synced
                        isDeleted = it.isDeleted,
                        localFilePath = it.localFilePath,
                        downloadStatus = it.downloadStatus,
                    )
                }
            }
        }

        else -> {}
    }
}
