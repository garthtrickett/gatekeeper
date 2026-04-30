package com.aegisgatekeeper.app

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GatekeeperStateManager {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sideEffectDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(sideEffectDispatcher + SupervisorJob())
    private val db = DatabaseManager.db

    private val initialState: GatekeeperState by lazy {
        // --- Database Seeding (one-time on first launch) ---
        db.appSettingsQueries.insertDefault()

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

        // --- Heal Intentional Slots (Point to freshest content item and remove deleted) ---
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
            db.customInterceptionMessageQueries
                .selectAll()
                .executeAsList()
                .associate { it.packageName to it.message }

        val alternativeActivitiesFromDb = db.alternativeActivityQueries.selectAll().executeAsList()
        val vaultItemsFromDb = db.vaultItemQueries.selectAll().executeAsList()
        val contentItemsFromDb = db.contentItemQueries.selectAllByRank().executeAsList()
        val sessionLogsFromDb = db.sessionLogQueries.selectAll().executeAsList()
        val slotsFromDb = db.intentionalSlotQueries.selectAll().executeAsList()
        val podcastSubscriptionsFromDb = db.podcastSubscriptionQueries.selectAll().executeAsList()

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

        val missionControlAppsFromDb =
            db.missionControlAppQueries
                .selectAll()
                .executeAsList()
                .map { it.packageName }

        val token = SecureTokenStorage.getToken()

        GatekeeperState(
            isProTier = appSettings?.isProTier ?: false,
            podcastSubscriptions =
                podcastSubscriptionsFromDb.map {
                    com.aegisgatekeeper.app.domain.PodcastSubscription(
                        id = it.id,
                        feedUrl = it.feedUrl,
                        showTitle = it.showTitle,
                        artworkUrl = it.artworkUrl,
                        lastModified = System.currentTimeMillis(),
                        isSynced = false,
                        isDeleted = false,
                    )
                },
            isAuthenticated = token != null,
            jwtToken = token,
            isManualLockdownActive = appSettings?.isManualLockdownActive ?: false,
            deepWorkStartMinutes = appSettings?.deepWorkStartMinutes?.toInt() ?: 540,
            deepWorkEndMinutes = appSettings?.deepWorkEndMinutes?.toInt() ?: 1020,
            gatheringStartMinutes = appSettings?.gatheringStartMinutes?.toInt() ?: 1080,
            gatheringEndMinutes = appSettings?.gatheringEndMinutes?.toInt() ?: 1110,
            activeFrictionGame = appSettings?.activeFrictionGame ?: com.aegisgatekeeper.app.domain.FrictionGame.GAUNTLET,
            missionControlApps = missionControlAppsFromDb,
            missionControlWebsites = pinnedWebsitesFromDb,
            appGroups = appGroupsList,
            customMessages = customMessagesFromDb,
            consumedCheckIns = consumedCheckInsList,
            alternativeActivities =
                alternativeActivitiesFromDb.map {
                    com.aegisgatekeeper.app.domain
                        .AlternativeActivity(it.id, it.description, it.createdAtTimestamp)
                },
            vaultItems =
                vaultItemsFromDb.map {
                    VaultItem(it.id, it.query, it.capturedAtTimestamp, it.isResolved, it.lastModified, it.isSynced, it.isDeleted)
                },
            contentItems =
                contentItemsFromDb.map {
                    ContentItem(
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
            handleMediaAndSystemEffects(action, oldState, newState, ::dispatch)
        }
    }

    private var lastDetectedPackage: String? = null
    private var ticksSinceLastUsageCheck = 0
    private val cachedUsageMinutes = mutableMapOf<String, Int>()

    /**
     * Centralized validation logic called by both the Foreground Heartbeat (Alpha)
     * and the Accessibility Event Stream (Omega) for zero-latency blocking.
     */
    fun performAppValidation(
        context: Context,
        currentApp: String,
    ) {
        val state = state.value
        val isNewApp = currentApp != lastDetectedPackage

        if (isNewApp) {
            if (lastDetectedPackage != null && state.activeWhitelists.containsKey(lastDetectedPackage)) {
                dispatch(GatekeeperAction.TriggerExitInterview(lastDetectedPackage!!))
            }
        }

        val activeGroups = state.appGroups.filter { it.apps.contains(currentApp) }

        Log.d("Gatekeeper", "👁️ Validating app: $currentApp | Active Groups Found: ${activeGroups.size}")

        if (activeGroups.isNotEmpty()) {
            val calendar = java.util.Calendar.getInstance()
            val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
            val currentDay =
                when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.MONDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY
                    java.util.Calendar.TUESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.TUESDAY
                    java.util.Calendar.WEDNESDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.WEDNESDAY
                    java.util.Calendar.THURSDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.THURSDAY
                    java.util.Calendar.FRIDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.FRIDAY
                    java.util.Calendar.SATURDAY -> com.aegisgatekeeper.app.domain.DayOfWeek.SATURDAY
                    else -> com.aegisgatekeeper.app.domain.DayOfWeek.SUNDAY
                }

            var isBlocked = false
            var blockReason = ""

            val checkUsage = isNewApp || ticksSinceLastUsageCheck >= 5
            if (checkUsage) ticksSinceLastUsageCheck = 0

            if (state.isManualLockdownActive) {
                isBlocked = true
                blockReason = "Manual Lockdown Engaged"
            } else {
                for (group in activeGroups) {
                    val groupViolations = mutableListOf<String>()
                    val enabledRules = group.rules.filter { it.isEnabled }

                    if (enabledRules.isEmpty()) continue

                    for (rule in enabledRules) {
                        when (rule) {
                                                        is com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock -> {
                                if (rule.daysOfWeek.contains(currentDay)) {
                                    val activeSlots = rule.timeSlots.filter { currentMinutes in it.startTimeMinutes..it.endTimeMinutes }
                                    if (activeSlots.isNotEmpty()) {
                                        val slotsStr = activeSlots.joinToString(", ") { 
                                            String.format("%02d:%02d - %02d:%02d", it.startTimeMinutes / 60, it.startTimeMinutes % 60, it.endTimeMinutes / 60, it.endTimeMinutes % 60)
                                        }
                                        groupViolations.add("Scheduled Block ($slotsStr)")
                                    }
                                }
                            }

                            is com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit -> {
                                val usageMinutes =
                                    if (checkUsage) {
                                        val usage = getDailyUsageMinutes(context, group.apps)
                                        cachedUsageMinutes[group.id] = usage
                                        usage
                                    } else {
                                        cachedUsageMinutes[group.id] ?: 0
                                    }
                                if (usageMinutes >= rule.timeLimitMinutes) {
                                    val timeLeft = maxOf(0, rule.timeLimitMinutes - usageMinutes)
                                    groupViolations.add("Time Limit Reached (${timeLeft}m left, used $usageMinutes/${rule.timeLimitMinutes}m)")
                                }
                            }

                            is com.aegisgatekeeper.app.domain.BlockingRule.CheckIn -> {
                                if (rule.daysOfWeek.contains(currentDay)) {
                                    val timesStr = rule.checkInTimesMinutes.sorted().joinToString(", ") {
                                        String.format("%02d:%02d", it / 60, it % 60)
                                    }
                                    groupViolations.add("Check-In Required ($timesStr)")
                                }
                            }

                            is com.aegisgatekeeper.app.domain.BlockingRule.DomainBlock -> {}

                            is com.aegisgatekeeper.app.domain.BlockingRule.AlwaysBlock -> {
                                groupViolations.add("Always Block")
                            }
                        }
                    }

                    val groupIsBlocked =
                        when (group.combinator) {
                            com.aegisgatekeeper.app.domain.RuleCombinator.ANY -> {
                                groupViolations.isNotEmpty()
                            }

                            com.aegisgatekeeper.app.domain.RuleCombinator.ALL -> {
                                groupViolations.size == enabledRules.size &&
                                    enabledRules.isNotEmpty()
                            }
                        }

                    if (groupIsBlocked) {
                        isBlocked = true
                        blockReason = "Policy Violation: " + groupViolations.joinToString(" AND ") + " for '${group.name}'"
                        break
                    }
                }
            }

            if (isBlocked) {
                dispatch(GatekeeperAction.RuleViolationDetected(currentApp, blockReason, System.currentTimeMillis()))
            } else if (isNewApp) {
                dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
            }

            ticksSinceLastUsageCheck++
        } else if (isNewApp) {
            dispatch(GatekeeperAction.AppBroughtToForeground(currentApp, System.currentTimeMillis()))
        }

        lastDetectedPackage = currentApp
    }

    private fun getDailyUsageMinutes(
        context: Context,
        packages: Set<String>,
    ): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        var totalTime = 0L
        for (pkg in packages) {
            stats[pkg]?.let { totalTime += it.totalTimeInForeground }
        }
        return (totalTime / 60000).toInt()
    }
}
