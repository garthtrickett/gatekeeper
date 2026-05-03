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

        val downloadStatusAdapter =
            object : ColumnAdapter<com.aegisgatekeeper.app.domain.DownloadStatus, String> {
                override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.DownloadStatus =
                    com.aegisgatekeeper.app.domain.DownloadStatus
                        .valueOf(databaseValue)

                override fun encode(value: com.aegisgatekeeper.app.domain.DownloadStatus): String = value.name
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

        val messageStatusAdapter =
            object : ColumnAdapter<com.aegisgatekeeper.app.domain.MessageStatus, String> {
                override fun decode(databaseValue: String): com.aegisgatekeeper.app.domain.MessageStatus =
                    com.aegisgatekeeper.app.domain.MessageStatus
                        .valueOf(databaseValue)

                override fun encode(value: com.aegisgatekeeper.app.domain.MessageStatus): String = value.name
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
                        downloadStatusAdapter = downloadStatusAdapter,
                    ),
                ScheduledMessageAdapter =
                    com.aegisgatekeeper.app.db.ScheduledMessage.Adapter(
                        statusAdapter = messageStatusAdapter,
                    ),
            )

        db.appSettingsQueries.insertDefault()

        mutableState = MutableStateFlow(GatekeeperState())
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun dispatchWithSideEffects(action: GatekeeperAction) {
        val oldState = mutableState.value
        val newState =
            com.aegisgatekeeper.app.domain
                .reduce(oldState, action)
        mutableState.value = newState
        com.aegisgatekeeper.app.effects.handleDatabaseEffects(action, oldState, newState, db) {
            dispatchWithSideEffects(it)
        }
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
            mutableState.value =
                GatekeeperState(
                    data =
                        com.aegisgatekeeper.app.domain
                            .DataState(vaultItems = listOf(initialItem)),
                )

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
                null,
                "vid1",
                "Video 1",
                null,
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                0L,
                100L,
                null,
                100L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
            )
            db.contentItemQueries.insert(
                "2",
                null,
                "aud1",
                "Audio 1",
                null,
                ContentSource.SOUNDCLOUD,
                ContentType.AUDIO,
                1L,
                200L,
                null,
                200L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
            )
            db.contentItemQueries.insert(
                "3",
                null,
                "vid2",
                "Video 2",
                null,
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                2L,
                300L,
                null,
                300L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
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
    fun testPodcastEpisodeQueries_selectAllLatestGlobal_sortsByLastModifiedDesc() =
        runTest {
            // Arrange: Create a subscription
            db.podcastSubscriptionQueries.insert("sub1", "https://test.com/feed", "Test Show", null)

            // Insert episodes with explicit lastModified values (simulating parsed pubDates)
            db.podcastEpisodeQueries.insertOrReplace("ep1", "sub1", "Older Episode", "url1", 1000L, "Jan 01", 10000L)
            db.podcastEpisodeQueries.insertOrReplace("ep2", "sub1", "Newer Episode", "url2", 1000L, "Jan 02", 20000L)
            db.podcastEpisodeQueries.insertOrReplace("ep3", "sub1", "Oldest Episode", "url3", 1000L, "Dec 31", 5000L)

            // Act
            val latest = db.podcastEpisodeQueries.selectAllLatestGlobal().executeAsList()

            // Assert: Should be sorted by lastModified DESC (Newer -> Older -> Oldest)
            assertThat(latest).hasSize(3)
            assertThat(latest[0].title).isEqualTo("Newer Episode")
            assertThat(latest[1].title).isEqualTo("Older Episode")
            assertThat(latest[2].title).isEqualTo("Oldest Episode")
        }

    @Test
    fun testContentItemQueries_limitsAndCounts() =
        runTest {
            // Arrange
            db.contentItemQueries.insert(
                "1",
                null,
                "vid1",
                "Video 1",
                null,
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                0L,
                100L,
                null,
                100L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
            )
            db.contentItemQueries.insert(
                "2",
                null,
                "aud1",
                "Audio 1",
                null,
                ContentSource.SOUNDCLOUD,
                ContentType.AUDIO,
                1L,
                200L,
                null,
                200L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
            )
            db.contentItemQueries.insert(
                "3",
                null,
                "vid2",
                "Video 2",
                null,
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                2L,
                300L,
                null,
                300L,
                false,
                false,
                null,
                com.aegisgatekeeper.app.domain.DownloadStatus.NONE,
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
    fun testUpdateCheckInRuleLogging() =
        runTest {
            // Arrange
            dispatchWithSideEffects(GatekeeperAction.CreateAppGroup("group1", "Group", emptySet()))
            dispatchWithSideEffects(
                GatekeeperAction.AddCheckInRule(
                    id = "checkin_1",
                    groupId = "group1",
                    checkInTimesMinutes = listOf(600),
                    durationMinutes = 15,
                    daysOfWeek = setOf(com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY),
                ),
            )

            // Act 1: Verify Initial Insertion
            var rules = db.blockingRuleQueries.selectAllCheckInRules().executeAsList()
            assertThat(rules).hasSize(1)
            assertThat(rules.first().durationMinutes).isEqualTo(15L)

            // Act 2: Update Rule
            dispatchWithSideEffects(
                GatekeeperAction.UpdateCheckInRule(
                    id = "checkin_1",
                    groupId = "group1",
                    checkInTimesMinutes = listOf(600, 720),
                    durationMinutes = 30,
                    daysOfWeek = setOf(com.aegisgatekeeper.app.domain.DayOfWeek.MONDAY),
                ),
            )

            // Assert 2: Verify Update
            rules = db.blockingRuleQueries.selectAllCheckInRules().executeAsList()
            assertThat(rules).hasSize(1)
            assertThat(rules.first().durationMinutes).isEqualTo(30L)
            assertThat(rules.first().checkInTimes).isEqualTo("600,720")
        }

    @Test
    fun testMissionControlAppsLogging() =
        runTest {
            // Arrange
            val action = GatekeeperAction.UpdateMissionControlApps(listOf("com.test.app1", "com.test.app2"))

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val apps = db.missionControlAppQueries.selectAll().executeAsList()
            assertThat(apps).hasSize(2)
            assertThat(apps[0].packageName).isEqualTo("com.test.app1")
            assertThat(apps[0].rank).isEqualTo(0L)
            assertThat(apps[1].packageName).isEqualTo("com.test.app2")
            assertThat(apps[1].rank).isEqualTo(1L)
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
    fun testAddAlwaysBlockRuleLogging() =
        runTest {
            // Arrange: Create a group first
            dispatchWithSideEffects(GatekeeperAction.CreateAppGroup("group1", "Test Group", emptySet()))

            val action = GatekeeperAction.AddAlwaysBlockRule(id = "always_block_1", groupId = "group1")

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val rules = db.blockingRuleQueries.selectAllRules().executeAsList()
            assertThat(rules).hasSize(1)
            assertThat(rules.first().ruleType).isEqualTo("ALWAYS_BLOCK")
            assertThat(rules.first().groupId).isEqualTo("group1")
        }

    @Test
    fun testUpdatePhaseWindowsLogging() =
        runTest {
            // Arrange
            val action = GatekeeperAction.UpdatePhaseWindows(480, 960, 1080, 1140)

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val settings = db.appSettingsQueries.getSettings().executeAsOne()
            assertThat(settings.deepWorkStartMinutes).isEqualTo(480L)
            assertThat(settings.deepWorkEndMinutes).isEqualTo(960L)
            assertThat(settings.gatheringStartMinutes).isEqualTo(1080L)
            assertThat(settings.gatheringEndMinutes).isEqualTo(1140L)
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

    @Test
    fun testScheduledMessageLogging_Lifecycle() =
        runTest {
            // Arrange
            val msg =
                com.aegisgatekeeper.app.domain.ScheduledMessage(
                    id = "msg1",
                    beeperRoomId = "room1",
                    chatName = "Test Chat",
                    messageText = "Hello World",
                    scheduledTimestamp = 12345L,
                )
            val action = GatekeeperAction.ScheduleMessage(msg)

            // Act 1: Schedule the message
            dispatchWithSideEffects(action)

            // Assert 1: Verify Initial Insertion
            var messages = db.scheduledMessageQueries.selectAll().executeAsList()
            assertThat(messages).hasSize(1)
            assertThat(messages.first().messageText).isEqualTo("Hello World")
            assertThat(messages.first().status.name).isEqualTo("PENDING")

            // Act 2: Simulate delivery success
            dispatchWithSideEffects(GatekeeperAction.MessageDelivered("msg1"))

            // Assert 2: Verify Status Update
            messages = db.scheduledMessageQueries.selectAll().executeAsList()
            assertThat(messages.first().status.name).isEqualTo("SENT")

            // Act 3: Simulate cancellation on a new message
            val msg2 = msg.copy(id = "msg2")
            dispatchWithSideEffects(GatekeeperAction.ScheduleMessage(msg2))
            dispatchWithSideEffects(GatekeeperAction.CancelScheduledMessage("msg2"))

            // Assert 3: Verify cancellation status
            messages = db.scheduledMessageQueries.selectAll().executeAsList()
            assertThat(messages).hasSize(2)
            val cancelledMsg = messages.first { it.id == "msg2" }
            assertThat(cancelledMsg.status.name).isEqualTo("CANCELLED")
        }
}
