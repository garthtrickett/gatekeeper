package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperEffect
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.parseRssPubDate
import com.aegisgatekeeper.app.domain.currentTimeMillis
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
                val itemsToDelete = db.contentItemQueries.selectAllByRank().executeAsList().filter { it.podcastId == effect.id }
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
                dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.PodcastEpisodesLoaded(cached, effect.podcastId))
            }
        }

        is GatekeeperEffect.DbCacheParsedEpisodes -> {
            platformLog("Gatekeeper", "🗄️ DB: Caching ${effect.episodes.size} episodes for podcast ${effect.podcastId}")
            db.transaction {
                effect.episodes.forEachIndexed { index, ep ->
                    val id = com.aegisgatekeeper.app.domain.randomUUIDString()
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
            dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.PodcastEpisodesLoaded(cached, effect.podcastId))
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
            dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.LatestGlobalEpisodesLoaded(episodes))
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
                db.blockingRuleQueries.insertConsumedCheckIn(effect.log.id, effect.log.groupId, effect.log.timeMinutes.toLong(), effect.log.timestamp)
            }
            if (effect.reason != null) {
                db.emergencyBypassLogQueries.insert(
                    id = com.aegisgatekeeper.app.domain.randomUUIDString(),
                    packageName = "Group: ${effect.groupId}",
                    reason = effect.reason,
                    timestamp = effect.timestamp,
                )
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
                id = com.aegisgatekeeper.app.domain.randomUUIDString(),
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
            dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.ExportDataGenerated(markdown))
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
                id = com.aegisgatekeeper.app.domain.randomUUIDString(),
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

fun deletedHandleDatabaseEffects_common() {}
    action: GatekeeperAction,
    oldState: GatekeeperState,
    newState: GatekeeperState,
    db: GatekeeperDatabase,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (action) {
        GatekeeperAction.LoadInitialState -> {
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

        is GatekeeperAction.SaveToVault -> {
            val newItem = (newState.data.vaultItems - oldState.data.vaultItems.toSet()).firstOrNull()
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
                val itemsToDelete = oldState.data.contentItems.filter { it.podcastId == action.id }
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
            val updatedOrNewItem = newState.data.contentItems.find { it.videoId == action.videoId && it.source == action.source }
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
            val item = newState.data.contentItems.find { it.id == actionId }
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
                newState.data.contentItems.forEach { item ->
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

            if (newState.media.activePodcastId == action.podcastId) {
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

            if (newState.media.latestGlobalEpisodes != null || newState.media.activePodcastId == null) {
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
            val log = (newState.data.consumedCheckIns - oldState.data.consumedCheckIns.toSet()).firstOrNull()
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
            val newLog = (newState.sync.notificationDigest - oldState.sync.notificationDigest.toSet()).firstOrNull()
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
            val newLog = (newState.data.sessionLogs - oldState.data.sessionLogs.toSet()).firstOrNull()
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
                rank =
                    newState.data.missionControlWebsites.size
                        .toLong(),
            )
        }

        is GatekeeperAction.RemovePinnedWebsite -> {
            platformLog("Gatekeeper", "🗄️ DB: Removing pinned website ${action.id}")
            db.missionControlWebsiteQueries.delete(id = action.id)
        }

        is GatekeeperAction.AddAlternativeActivity -> {
            val newActivity = (newState.data.alternativeActivities - oldState.data.alternativeActivities.toSet()).firstOrNull()
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
