package com.aegisgatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.aegisgatekeeper.app.db.ContentItem
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.db.SessionLog
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.VaultItem
import com.aegisgatekeeper.app.domain.reduce
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration test for the GatekeeperStateManager and its side effects.
 * This test runs on an emulator/device and uses an in-memory SQLDelight database
 * to verify that dispatching actions correctly triggers database writes.
 */
@RunWith(AndroidJUnit4::class)
class GatekeeperStateManagerTest {
    private lateinit var db: GatekeeperDatabase
    private lateinit var driver: AndroidSqliteDriver

    // We are creating a manual, test-only version of the StateManager's logic
    // to inject our in-memory database.
    private lateinit var mutableState: MutableStateFlow<GatekeeperState>
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Use the Android driver. Passing 'null' as the name creates an in-memory DB.
        driver =
            AndroidSqliteDriver(
                schema = GatekeeperDatabase.Schema,
                context = context,
                name = null,
                callback =
                    object : AndroidSqliteDriver.Callback(GatekeeperDatabase.Schema) {
                        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.setForeignKeyConstraintsEnabled(true)
                        }
                    },
            )

        // We must provide the same ColumnAdapter used in production
        val emotionAdapter =
            object : ColumnAdapter<Emotion, String> {
                override fun decode(databaseValue: String): Emotion = Emotion.valueOf(databaseValue)

                override fun encode(value: Emotion): String = value.name
            }

        val contentSourceAdapter =
            object : ColumnAdapter<ContentSource, String> {
                override fun decode(databaseValue: String): ContentSource = ContentSource.valueOf(databaseValue)

                override fun encode(value: ContentSource): String = value.name
            }

        val contentTypeAdapter =
            object : ColumnAdapter<ContentType, String> {
                override fun decode(databaseValue: String): ContentType = ContentType.valueOf(databaseValue)

                override fun encode(value: ContentType): String = value.name
            }

        val frictionGameAdapter =
            object : ColumnAdapter<com.aegisgatekeeper.app.domain.FrictionGame, String> {
                override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.FrictionGame =
                    com.aegisgatekeeper.app.domain.FrictionGame
                        .valueOf(databaseValue)

                override fun encode(value: com.aegisgatekeeper.app.domain.FrictionGame): String = value.name
            }

        val ruleCombinatorAdapter =
            object : ColumnAdapter<com.aegisgatekeeper.app.domain.RuleCombinator, String> {
                override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.RuleCombinator =
                    com.aegisgatekeeper.app.domain.RuleCombinator
                        .valueOf(databaseValue)

                override fun encode(value: com.aegisgatekeeper.app.domain.RuleCombinator): String = value.name
            }

        db =
            GatekeeperDatabase(
                driver = driver,
                AppGroupAdapter =
                    com.aegisgatekeeper.app.db.AppGroup.Adapter(
                        ruleCombinatorAdapter = ruleCombinatorAdapter,
                    ),
                AppSettingsAdapter =
                    com.aegisgatekeeper.app.db.AppSettings.Adapter(
                        activeFrictionGameAdapter = frictionGameAdapter,
                    ),
                SessionLogAdapter = SessionLog.Adapter(emotionAdapter = emotionAdapter),
                ContentItemAdapter =
                    ContentItem.Adapter(
                        sourceAdapter = contentSourceAdapter,
                        typeAdapter = contentTypeAdapter,
                    ),
            )

        mutableState = MutableStateFlow(GatekeeperState())
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun testEmergencyBypassLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.EmergencyBypassRequested(
                    packageName = "com.test.app",
                    reason = "Test reason",
                    allocatedDurationMillis = 300_000L,
                    currentTimestamp = 12345L,
                )

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val logs = db.emergencyBypassLogQueries.selectAll().executeAsList()
            assertThat(logs).hasSize(1)

            val log = logs.first()
            assertThat(log.packageName).isEqualTo("com.test.app")
            assertThat(log.reason).isEqualTo("Test reason")
            assertThat(log.timestamp).isEqualTo(12345L)
        }

    @Test
    fun testMarkVaultItemResolvedLogging() =
        runTest {
            // Arrange
            val initialItem = VaultItem(query = "Test resolve", capturedAtTimestamp = 1000L)
            db.vaultItemQueries.insert(
                id = initialItem.id,
                query = initialItem.query,
                capturedAtTimestamp = initialItem.capturedAtTimestamp,
                isResolved = false,
                lastModified = initialItem.lastModified,
                isSynced = initialItem.isSynced,
                isDeleted = initialItem.isDeleted,
            )
            mutableState.value = GatekeeperState(vaultItems = listOf(initialItem))

            val action = GatekeeperAction.MarkVaultItemResolved(initialItem.id, System.currentTimeMillis())

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val items = db.vaultItemQueries.selectAll().executeAsList()
            val updatedItem = items.first { it.id == initialItem.id }
            assertThat(updatedItem.isResolved).isTrue()
        }

    @Test
    fun testLogSessionMetacognitionLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.LogSessionMetacognition(
                    packageName = "com.test.youtube",
                    durationMillis = 15000L,
                    emotion = Emotion.DRAINED,
                    currentTimestamp = 9999L,
                )

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val logs = db.sessionLogQueries.selectAll().executeAsList()
            assertThat(logs).hasSize(1)

            val log = logs.first()
            assertThat(log.packageName).isEqualTo("com.test.youtube")
            assertThat(log.durationMillis).isEqualTo(15000L)
            assertThat(log.emotion).isEqualTo(Emotion.DRAINED)
            assertThat(log.loggedAtTimestamp).isEqualTo(9999L)
        }

    @Test
    fun testSetCustomInterceptionMessageLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.SetCustomInterceptionMessage(
                    packageName = "com.test.twitter",
                    message = "Is Twitter really going to make you happier right now?",
                )

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val messages = db.customInterceptionMessageQueries.selectAll().executeAsList()
            assertThat(messages).hasSize(1)
            assertThat(messages.first().packageName).isEqualTo("com.test.twitter")
            assertThat(messages.first().message).isEqualTo("Is Twitter really going to make you happier right now?")
        }

    @Test
    fun testContentItemQueries_selectAllByRankAndType() =
        runTest {
            // Arrange
            db.contentItemQueries.insert(
                "1",
                "vid1",
                "Video 1",
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                0L,
                100L,
                null,
                100L,
                false,
                false,
            )
            db.contentItemQueries.insert(
                "2",
                "aud1",
                "Audio 1",
                ContentSource.SOUNDCLOUD,
                ContentType.AUDIO,
                1L,
                200L,
                null,
                200L,
                false,
                false,
            )
            db.contentItemQueries.insert(
                "3",
                "vid2",
                "Video 2",
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                2L,
                300L,
                null,
                300L,
                false,
                false,
            )

            // Act
            val videoItems = db.contentItemQueries.selectAllByRankAndType(ContentType.VIDEO).executeAsList()
            val audioItems = db.contentItemQueries.selectAllByRankAndType(ContentType.AUDIO).executeAsList()

            // Assert
            assertThat(videoItems).hasSize(2)
            assertThat(videoItems[0].title).isEqualTo("Video 1")
            assertThat(videoItems[1].title).isEqualTo("Video 2")

            assertThat(audioItems).hasSize(1)
            assertThat(audioItems[0].title).isEqualTo("Audio 1")
        }

    @Test
    fun testContentItemQueries_limitsAndCounts() =
        runTest {
            // Arrange
            db.contentItemQueries.insert(
                "1",
                "vid1",
                "Video 1",
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                0L,
                100L,
                null,
                100L,
                false,
                false,
            )
            db.contentItemQueries.insert(
                "2",
                "aud1",
                "Audio 1",
                ContentSource.SOUNDCLOUD,
                ContentType.AUDIO,
                1L,
                200L,
                null,
                200L,
                false,
                false,
            )
            db.contentItemQueries.insert(
                "3",
                "vid2",
                "Video 2",
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                2L,
                300L,
                null,
                300L,
                false,
                false,
            )

            // Act & Assert - countAll
            assertThat(db.contentItemQueries.countAll().executeAsOne()).isEqualTo(3L)
            assertThat(db.contentItemQueries.countAllByType(ContentType.VIDEO).executeAsOne()).isEqualTo(2L)
            assertThat(db.contentItemQueries.countAllByType(ContentType.AUDIO).executeAsOne()).isEqualTo(1L)

            // Act & Assert - Limits
            val limitedAll = db.contentItemQueries.selectAllByRankLimit(1L).executeAsList()
            assertThat(limitedAll).hasSize(1)
            assertThat(limitedAll.first().title).isEqualTo("Video 1")

            val unlimitedAll = db.contentItemQueries.selectAllByRankLimit(-1L).executeAsList()
            assertThat(unlimitedAll).hasSize(3)

            val limitedVideo = db.contentItemQueries.selectAllByRankAndTypeLimit(ContentType.VIDEO, 1L).executeAsList()
            assertThat(limitedVideo).hasSize(1)
            assertThat(limitedVideo.first().title).isEqualTo("Video 1")
        }

    @Test
    fun testDomainBlockRuleLogging() =
        runTest {
            // Arrange: Create a group first to satisfy foreign key constraints
            dispatchWithSideEffects(
                GatekeeperAction.CreateAppGroup(
                    id = "group1",
                    name = "Blocklist",
                    apps = setOf("com.test.app"),
                ),
            )

            val action =
                GatekeeperAction.AddDomainBlockRule(
                    id = "domain_rule_1",
                    groupId = "group1",
                    domains = setOf("reddit.com", "twitter.com"),
                )

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val rules = db.blockingRuleQueries.selectAllRules().executeAsList()
            assertThat(rules).hasSize(1)
            assertThat(rules.first().ruleType).isEqualTo("DOMAIN_BLOCK")

            val domains = db.domainBlockRuleQueries.selectAll().executeAsList()
            assertThat(domains).hasSize(1)
            assertThat(domains.first().domains).isEqualTo("reddit.com,twitter.com")

            // Act 2: Test Updating
            val updateAction =
                GatekeeperAction.UpdateDomainBlockRule(
                    ruleId = "domain_rule_1",
                    groupId = "group1",
                    domains = setOf("reddit.com", "youtube.com"),
                )
            dispatchWithSideEffects(updateAction)

            // Assert 2
            val updatedDomains = db.domainBlockRuleQueries.selectAll().executeAsList()
            assertThat(updatedDomains).hasSize(1)
            assertThat(updatedDomains.first().domains).isEqualTo("reddit.com,youtube.com")
        }

    @Test
    fun testMissionControlWebsiteLogging() =
        runTest {
            // Arrange
            val addAction =
                GatekeeperAction.AddPinnedWebsite(
                    id = "site1",
                    label = "Example",
                    url = "https://example.com",
                )

            // Act
            dispatchWithSideEffects(addAction)

            // Assert
            var sites = db.missionControlWebsiteQueries.selectAll().executeAsList()
            assertThat(sites).hasSize(1)
            assertThat(sites.first().label).isEqualTo("Example")

            // Act 2: Test Removing
            val removeAction = GatekeeperAction.RemovePinnedWebsite("site1")
            dispatchWithSideEffects(removeAction)

            // Assert 2
            sites = db.missionControlWebsiteQueries.selectAll().executeAsList()
            assertThat(sites).isEmpty()
        }

    @Test
    fun testSaveToVaultLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.SaveToVault(
                    query = "How to test SQLDelight?",
                    currentTimestamp = 5555L,
                )

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val items = db.vaultItemQueries.selectAll().executeAsList()
            assertThat(items).hasSize(1)

            val item = items.first()
            assertThat(item.query).isEqualTo("How to test SQLDelight?")
            assertThat(item.capturedAtTimestamp).isEqualTo(5555L)
            assertThat(item.isResolved).isFalse()
        }

    /**
     * A test-specific replica of the dispatch and side-effect handling logic.
     * It returns a Job so the test can wait for the DB write to finish.
     */
    private suspend fun dispatchWithSideEffects(action: GatekeeperAction) {
        val oldState = mutableState.value
        val newState = reduce(oldState, action)
        mutableState.value = newState

        // Mimic the real side-effect logic
        val job =
            scope.launch {
                when (action) {
                    is GatekeeperAction.EmergencyBypassRequested -> {
                        db.emergencyBypassLogQueries.insert(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            packageName = action.packageName,
                            reason = action.reason,
                            timestamp = action.currentTimestamp,
                        )
                    }

                    is GatekeeperAction.SaveToVault -> {
                        val newItem = (newState.vaultItems - oldState.vaultItems.toSet()).first()
                        db.vaultItemQueries.insert(
                            id = newItem.id,
                            query = newItem.query,
                            capturedAtTimestamp = newItem.capturedAtTimestamp,
                            isResolved = newItem.isResolved,
                            lastModified = newItem.lastModified,
                            isSynced = newItem.isSynced,
                            isDeleted = newItem.isDeleted,
                        )
                    }

                    is GatekeeperAction.MarkVaultItemResolved -> {
                        db.vaultItemQueries.markAsResolved(action.currentTimestamp, action.id)
                    }

                    is GatekeeperAction.SaveToContentBank -> {
                        val newItem = newState.contentItems.find { it.videoId == action.videoId && it.source == action.source }!!
                        db.contentItemQueries.insert(
                            id = newItem.id,
                            videoId = newItem.videoId,
                            title = newItem.title,
                            source = newItem.source,
                            type = newItem.type,
                            rank = newItem.rank,
                            capturedAtTimestamp = newItem.capturedAtTimestamp,
                            durationSeconds = newItem.durationSeconds,
                            lastModified = newItem.lastModified,
                            isSynced = newItem.isSynced,
                            isDeleted = newItem.isDeleted,
                        )
                    }

                    is GatekeeperAction.ReorderContentBank -> {
                        newState.contentItems.forEach { item ->
                            db.contentItemQueries.updateRank(rank = item.rank, lastModified = action.currentTimestamp, id = item.id)
                        }
                    }

                    is GatekeeperAction.RemoveFromContentBank -> {
                        db.contentItemQueries.delete(lastModified = action.currentTimestamp, id = action.id)
                    }

                    is GatekeeperAction.LogSessionMetacognition -> {
                        val newLog = (newState.sessionLogs - oldState.sessionLogs.toSet()).first()
                        db.sessionLogQueries.insert(
                            id = newLog.id,
                            packageName = newLog.packageName,
                            durationMillis = newLog.durationMillis,
                            emotion = newLog.emotion,
                            loggedAtTimestamp = newLog.loggedAtTimestamp,
                        )
                    }

                    is GatekeeperAction.SaveIntentionalSlot -> {
                        db.intentionalSlotQueries.insert(
                            slotIndex = action.slotIndex.toLong(),
                            contentItemId = action.contentItem.id,
                        )
                    }

                    is GatekeeperAction.ClearIntentionalSlot -> {
                        db.intentionalSlotQueries.delete(slotIndex = action.slotIndex.toLong())
                    }

                    GatekeeperAction.UpgradeToProTier -> {
                        db.appSettingsQueries.updateProStatus(true)
                    }

                    is GatekeeperAction.SetFrictionGame -> {
                        db.appSettingsQueries.updateFrictionGame(action.game)
                    }

                    is GatekeeperAction.LogGiveUp -> {
                        db.giveUpLogQueries.insert(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            packageName = action.packageName,
                            timestamp = action.currentTimestamp,
                        )
                    }

                    is GatekeeperAction.SetCustomInterceptionMessage -> {
                        db.customInterceptionMessageQueries.insert(
                            packageName = action.packageName,
                            message = action.message,
                        )
                    }

                    is GatekeeperAction.RemoveCustomInterceptionMessage -> {
                        db.customInterceptionMessageQueries.delete(action.packageName)
                    }

                    is GatekeeperAction.CreateAppGroup -> {
                        db.appGroupQueries.insertGroup(action.id, action.name, action.combinator)
                        action.apps.forEach { app ->
                            db.appGroupQueries.insertGroupedApp(action.id, app)
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
                        db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "DOMAIN_BLOCK", true)
                        db.domainBlockRuleQueries.insert(action.id, action.domains.joinToString(","))
                    }

                    is GatekeeperAction.UpdateDomainBlockRule -> {
                        db.domainBlockRuleQueries.insert(action.ruleId, action.domains.joinToString(","))
                    }

                    is GatekeeperAction.AddTimeLimitRule -> {
                        db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "TIME_LIMIT", true)
                        db.blockingRuleQueries.insertTimeLimitRule(action.id, action.timeLimitMinutes.toLong())
                    }

                    is GatekeeperAction.AddScheduledBlockRule -> {
                        db.blockingRuleQueries.insertBlockingRule(action.id, action.groupId, "SCHEDULED", true)
                        db.blockingRuleQueries.insertScheduledBlockRule(
                            action.id,
                            action.timeSlots.joinToString(",") { "${it.startTimeMinutes}-${it.endTimeMinutes}" },
                            action.daysOfWeek.joinToString(","),
                        )
                    }

                    is GatekeeperAction.DeleteRule -> {
                        db.blockingRuleQueries.deleteBlockingRule(action.ruleId)
                    }

                    is GatekeeperAction.ToggleRule -> {
                        db.blockingRuleQueries.updateRuleEnabled(action.isEnabled, action.ruleId)
                    }

                    is GatekeeperAction.UpdateMissionControlApps -> {
                        db.transaction {
                            db.missionControlAppQueries.deleteAll()
                            action.packageNames.forEachIndexed { index, packageName ->
                                db.missionControlAppQueries.insert(packageName, index.toLong())
                            }
                        }
                    }

                    is GatekeeperAction.SaveMediaPosition -> {
                        db.mediaPositionQueries.insert(action.mediaId, action.positionSeconds.toDouble())
                    }

                    is GatekeeperAction.AddPinnedWebsite -> {
                        db.missionControlWebsiteQueries.insert(
                            id = action.id,
                            label = action.label,
                            url = action.url,
                            rank = newState.missionControlWebsites.size.toLong(),
                        )
                    }

                    is GatekeeperAction.RemovePinnedWebsite -> {
                        db.missionControlWebsiteQueries.delete(id = action.id)
                    }

                    else -> { /* Other side effects not under test */ }
                }
            }
        // Wait for the background work to finish before returning
        job.join()
    }
}
