package com.aegisgatekeeper.app

import android.util.Log
import com.aegisgatekeeper.app.api.YoutubeApiClient
import com.aegisgatekeeper.app.auth.SecureTokenStorage
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.SessionLog
import com.aegisgatekeeper.app.domain.VaultItem
import com.aegisgatekeeper.app.domain.reduce
import com.aegisgatekeeper.app.effects.handleDatabaseEffects
import com.aegisgatekeeper.app.effects.handleMediaAndSystemEffects
import com.aegisgatekeeper.app.effects.handleSyncAndAuthEffects
import com.aegisgatekeeper.app.widget.VaultWidget
import com.aegisgatekeeper.app.widget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GatekeeperStateManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val db = DatabaseManager.db

    private val initialState: GatekeeperState by lazy {
        // --- Database Seeding (one-time on first launch) ---
        if (db.appGroupQueries
                .selectAllGroups()
                .executeAsList()
                .isEmpty()
        ) {
            Log.i("Gatekeeper", "DB: Seeding initial app groups...")
            val initialApps = setOf("com.android.chrome", "org.mozilla.firefox", "com.instagram.android")
            val groupId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            db.transaction {
                db.appGroupQueries.insertGroup(groupId, "Distractions", com.aegisgatekeeper.app.domain.RuleCombinator.ANY)
                initialApps.forEach { packageName ->
                    db.appGroupQueries.insertGroupedApp(groupId, packageName)
                }
            }
        }

        // --- Load initial state from database ---
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
                                    com.aegisgatekeeper.app.domain.BlockingRule.CheckIn(
                                        id = rule.id,
                                        groupId = rule.groupId,
                                        isEnabled = rule.isEnabled,
                                        checkInTimesMinutes =
                                            ci.checkInTimes
                                                .split(",")
                                                .filter { it.isNotEmpty() }
                                                .map { it.toInt() },
                                        durationMinutes = ci.durationMinutes.toInt(),
                                        daysOfWeek =
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
                                domainBlocks[rule.id]?.let { db ->
                                    com.aegisgatekeeper.app.domain.BlockingRule.DomainBlock(
                                        id = rule.id,
                                        groupId = rule.groupId,
                                        isEnabled = rule.isEnabled,
                                        domains =
                                            db.domains
                                                .split(",")
                                                .filter { it.isNotEmpty() }
                                                .toSet(),
                                    )
                                }
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
            db.customInterceptionMessageQueries
                .selectAll()
                .executeAsList()
                .associate { it.packageName to it.message }
        val vaultItemsFromDb = db.vaultItemQueries.selectAll().executeAsList()
        val contentItemsFromDb = db.contentItemQueries.selectAllByRank().executeAsList()
        val sessionLogsFromDb = db.sessionLogQueries.selectAll().executeAsList()
        val slotsFromDb = db.intentionalSlotQueries.selectAll().executeAsList()

        val mediaPositionsFromDb =
            db.mediaPositionQueries
                .selectAll()
                .executeAsList()
                .associate { it.mediaId to it.positionSeconds.toFloat() }

        val appSettings = db.appSettingsQueries.getSettings().executeAsOneOrNull()

        val pinnedWebsitesFromDb =
            db.missionControlWebsiteQueries.selectAll().executeAsList().map {
                com.aegisgatekeeper.app.domain
                    .PinnedWebsite(it.id, it.label, it.url)
            }

        val token = SecureTokenStorage.getToken()

        GatekeeperState(
            isProTier = appSettings?.isProTier ?: false,
            isAuthenticated = token != null,
            jwtToken = token,
            isManualLockdownActive = appSettings?.isManualLockdownActive ?: false,
            activeFrictionGame = appSettings?.activeFrictionGame ?: com.aegisgatekeeper.app.domain.FrictionGame.GAUNTLET,
            missionControlWebsites = pinnedWebsitesFromDb,
            appGroups = appGroupsList,
            customMessages = customMessagesFromDb,
            consumedCheckIns = consumedCheckInsList,
            vaultItems =
                vaultItemsFromDb.map {
                    VaultItem(it.id, it.query, it.capturedAtTimestamp, it.isResolved, it.lastModified, it.isSynced, it.isDeleted)
                },
            contentItems =
                contentItemsFromDb.map {
                    ContentItem(
                        it.id,
                        it.videoId,
                        it.title,
                        it.source,
                        it.type,
                        it.rank,
                        it.capturedAtTimestamp,
                        it.durationSeconds,
                        it.lastModified,
                        it.isSynced,
                        it.isDeleted,
                    )
                },
            savedMediaPositions = mediaPositionsFromDb,
            sessionLogs =
                sessionLogsFromDb.map {
                    SessionLog(
                        it.id,
                        it.packageName,
                        it.durationMillis,
                        it.emotion,
                        it.loggedAtTimestamp,
                    )
                },
            intentionalSlots =
                slotsFromDb.map {
                    com.aegisgatekeeper.app.domain.IntentionalSlotItem(
                        slotIndex = it.slotIndex.toInt(),
                        contentItem =
                            com.aegisgatekeeper.app.domain.ContentItem(
                                it.id,
                                it.videoId,
                                it.title,
                                it.source,
                                it.type,
                                it.rank,
                                it.capturedAtTimestamp,
                                it.durationSeconds,
                                it.lastModified,
                                it.isSynced,
                                it.isDeleted,
                            ),
                    )
                },
        )
    }

    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    fun dispatch(action: GatekeeperAction) {
        val actionName = action::class.simpleName ?: "UnknownAction"

        if (action !is GatekeeperAction.AppBroughtToForeground) {
            Log.d("Gatekeeper", "📥 Action Dispatched: $actionName")
        } else {
            if (_state.value.appGroups.any { it.apps.contains(action.packageName) }) {
                Log.d("Gatekeeper", "📥 Action Dispatched: $actionName (${action.packageName})")
            }
        }

        val currentState = _state.value
        val newState = reduce(currentState, action)

        // Only update state flow if the state has actually changed.
        if (newState != currentState) {
            _state.value = newState
        }

        // Always evaluate side effects, as some actions are pure side-effects (e.g. ProcessSharedLink)
        handleSideEffects(action, currentState, newState)
    }

    private fun handleSideEffects(
        action: GatekeeperAction,
        oldState: GatekeeperState,
        newState: GatekeeperState,
    ) {
        scope.launch {
            handleDatabaseEffects(action, oldState, newState, db, ::dispatch)
            handleSyncAndAuthEffects(action, newState, db)
            handleMediaAndSystemEffects(action, newState, ::dispatch)
        }
    }
}
