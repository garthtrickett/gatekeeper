package com.aegisgatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure, JVM-based unit tests for the top-level reduce function.
 * This confirms that given a specific state and action, the business logic
 * consistently produces the expected new state, with zero side effects.
 */
class GatekeeperReducerTest {
    private val blacklistedApp = "com.test.blacklisted"
    private val whitelistedApp = "com.test.whitelisted"

    private val initialState =
        GatekeeperState(
            interception =
                InterceptionState(
                    appGroups =
                        listOf(
                            AppGroup(id = "group1", name = "Test", apps = setOf(blacklistedApp), combinator = RuleCombinator.ANY),
                        ),
                ),
        )

    @Test
    fun testRuleViolationDetected_NoWhitelist_TriggersOverlay() {
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)
        val newState = reduce(initialState, action).state
        assertThat(newState.interception.isOverlayActive).isTrue()
        assertThat(newState.interception.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        assertThat(newState.interception.activeBlockReason).isEqualTo("Limit Reached")
    }

    @Test
    fun testAppBroughtToForeground_NotBlacklisted_DoesNothing() {
        val action = GatekeeperAction.AppBroughtToForeground(whitelistedApp, 1000L)
        val newState = reduce(initialState, action).state
        assertThat(
            newState,
        ).isEqualTo(initialState.copy(interception = initialState.interception.copy(activeForegroundApp = whitelistedApp)))
    }

    @Test
    fun testRuleViolationDetected_Whitelisted_DoesNothing() {
        val stateWithWhitelist =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        activeWhitelists =
                            mapOf(
                                blacklistedApp to
                                    TemporaryWhitelist(
                                        packageName = blacklistedApp,
                                        reason = "Test",
                                        grantedAtTimestamp = 500L,
                                        expiresAtTimestamp = 1500L,
                                        allocatedDurationMillis = 300_000L,
                                    ),
                            ),
                    ),
            )
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)
        val newState = reduce(stateWithWhitelist, action).state
        assertThat(
            newState,
        ).isEqualTo(stateWithWhitelist.copy(interception = stateWithWhitelist.interception.copy(activeForegroundApp = blacklistedApp)))
    }

    @Test
    fun testRuleViolationDetected_ExpiredWhitelist_TriggersOverlay() {
        val stateWithExpiredWhitelist =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        activeWhitelists =
                            mapOf(
                                blacklistedApp to
                                    TemporaryWhitelist(
                                        packageName = blacklistedApp,
                                        reason = "Test",
                                        grantedAtTimestamp = 100L,
                                        expiresAtTimestamp = 500L,
                                        allocatedDurationMillis = 300_000L,
                                    ),
                            ),
                    ),
            )
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)
        val newState = reduce(stateWithExpiredWhitelist, action).state
        assertThat(newState.interception.isOverlayActive).isTrue()
        assertThat(newState.interception.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        assertThat(newState.interception.expiredSessionDurationMillis).isEqualTo(300_000L)
        assertThat(newState.interception.activeBlockReason).isEqualTo("Limit Reached")
    }

    @Test
    fun testSessionExpired_TriggersOverlayAndSetsExpiredDuration() {
        val activeState =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        activeWhitelists =
                            mapOf(
                                blacklistedApp to TemporaryWhitelist(blacklistedApp, null, 0L, 1000L, 600_000L),
                            ),
                    ),
            )
        val action = GatekeeperAction.SessionExpired(blacklistedApp, 600_000L)
        val newState = reduce(activeState, action).state
        assertThat(newState.interception.isOverlayActive).isTrue()
        assertThat(newState.interception.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        assertThat(newState.interception.expiredSessionDurationMillis).isEqualTo(600_000L)
        assertThat(newState.interception.activeWhitelists).isEmpty()
    }

    @Test
    fun testEmergencyBypassRequested_GrantsWhitelist_DismissesOverlay() {
        val stateWithOverlay =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        isOverlayActive = true,
                        currentlyInterceptedApp = blacklistedApp,
                        expiredSessionDurationMillis = 600_000L,
                        activeBlockReason = "Time Limit Reached",
                    ),
            )
        val action =
            GatekeeperAction.EmergencyBypassRequested(
                packageName = blacklistedApp,
                reason = "I need to call an Uber",
                allocatedDurationMillis = 300_000L,
                currentTimestamp = 1000L,
            )
        val update = reduce(stateWithOverlay, action)
        val newState = update.state
        assertThat(newState.interception.isOverlayActive).isFalse()
        assertThat(newState.interception.currentlyInterceptedApp).isNull()
        assertThat(newState.interception.expiredSessionDurationMillis).isNull()
        assertThat(newState.interception.activeBlockReason).isNull()
        assertThat(newState.interception.activeWhitelists).containsKey(blacklistedApp)
        val whitelist = newState.interception.activeWhitelists[blacklistedApp]!!
        assertThat(whitelist.reason).isEqualTo("I need to call an Uber")
        assertThat(whitelist.expiresAtTimestamp).isEqualTo(1000L + 300_000L)
        assertThat(newState.data.analyticsBypasses).isEqualTo(1)
        assertThat(update.effects.any { it is GatekeeperEffect.LaunchAppAndStartSession && it.packageName == blacklistedApp }).isTrue()
    }

    @Test
    fun testDismissOverlay_ClearsState() {
        val stateWithOverlay =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        isOverlayActive = true,
                        currentlyInterceptedApp = blacklistedApp,
                    ),
            )
        val newState = reduce(stateWithOverlay, GatekeeperAction.DismissOverlay).state
        assertThat(newState.interception.isOverlayActive).isFalse()
        assertThat(newState.interception.currentlyInterceptedApp).isNull()
    }

    @Test
    fun testMarkVaultItemResolved_UpdatesItemStatus() {
        val item = VaultItem(id = "123", query = "Test Query", capturedAtTimestamp = 1000L)
        val stateWithItem = initialState.copy(data = initialState.data.copy(vaultItems = listOf(item)))
        val action = GatekeeperAction.MarkVaultItemResolved("123", 1000L)
        val update = reduce(stateWithItem, action)
        val newState = update.state
        assertThat(newState.data.vaultItems).hasSize(1)
        assertThat(
            newState.data.vaultItems
                .first()
                .isResolved,
        ).isTrue()
        assertThat(update.effects.any { it is GatekeeperEffect.DbMarkVaultItemResolved && it.id == "123" }).isTrue()
    }

    @Test
    fun testSaveToVault_AppendsToVaultItems() {
        val action = GatekeeperAction.SaveToVault("best standing desks", 2000L)
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.data.vaultItems).hasSize(1)
        val savedItem = newState.data.vaultItems.first()
        assertThat(savedItem.query).isEqualTo("best standing desks")
        assertThat(savedItem.capturedAtTimestamp).isEqualTo(2000L)
        assertThat(savedItem.isResolved).isFalse()
        assertThat(update.effects.any { it is GatekeeperEffect.DbInsertVaultItem && it.item == savedItem }).isTrue()
    }

    @Test
    fun testSetCustomInterceptionMessage_UpdatesMap() {
        val action = GatekeeperAction.SetCustomInterceptionMessage("com.reddit.frontpage", "Read a book instead.")
        val newState = reduce(initialState, action).state
        assertThat(newState.data.customMessages["com.reddit.frontpage"]).isEqualTo("Read a book instead.")
    }

    @Test
    fun testRemoveCustomInterceptionMessage_RemovesFromMap() {
        val stateWithMsg =
            initialState.copy(
                data =
                    initialState.data.copy(
                        customMessages =
                            mapOf("com.reddit.frontpage" to "Read a book instead."),
                    ),
            )
        val action = GatekeeperAction.RemoveCustomInterceptionMessage("com.reddit.frontpage")
        val newState = reduce(stateWithMsg, action).state
        assertThat(newState.data.customMessages).isEmpty()
    }

    @Test
    fun testProcessSharedLink_SetsIsProcessingLinkToTrue_AndEmitsCorrectEffect() {
        // Test YouTube Link (Should not flag SoundCloud or Generic)
        val ytAction = GatekeeperAction.ProcessSharedLink(url = "https://youtu.be/dQw4w9WgXcQ", currentTimestamp = 1000L)
        val ytUpdate = reduce(initialState, ytAction)
        assertThat(ytUpdate.state.media.isProcessingLink).isTrue()
        val ytEffect = ytUpdate.effects.filterIsInstance<GatekeeperEffect.FetchUrlMetadata>().first()
        assertThat(ytEffect.url).isEqualTo("https://youtu.be/dQw4w9WgXcQ")
        assertThat(ytEffect.isSoundCloud).isFalse()
        assertThat(ytEffect.isGeneric).isFalse()

        // Test SoundCloud Link
        val scAction = GatekeeperAction.ProcessSharedLink(url = "https://soundcloud.com/test", currentTimestamp = 1000L)
        val scUpdate = reduce(initialState, scAction)
        val scEffect = scUpdate.effects.filterIsInstance<GatekeeperEffect.FetchUrlMetadata>().first()
        assertThat(scEffect.isSoundCloud).isTrue()
        assertThat(scEffect.isGeneric).isFalse()

        // Test Generic Link
        val genericAction = GatekeeperAction.ProcessSharedLink(url = "https://example.com/article", currentTimestamp = 1000L)
        val genericUpdate = reduce(initialState, genericAction)
        val genericEffect = genericUpdate.effects.filterIsInstance<GatekeeperEffect.FetchUrlMetadata>().first()
        assertThat(genericEffect.isSoundCloud).isFalse()
        assertThat(genericEffect.isGeneric).isTrue()
    }

    @Test
    fun testAddEpisodeToBank_AppendsToContentBankAndEmitsDbEffect() {
        val episode = CachedEpisode("1", "pod1", "Test Ep", "https://audio.mp3", 3600L, "Jan 1")
        val action = GatekeeperAction.AddEpisodeToBank(episode, "pod1", "My Podcast")

        val update = reduce(initialState, action)
        val newState = update.state

        // 1. Verify a ContentItem is added to the state
        assertThat(newState.data.contentItems).hasSize(1)
        val newItem = newState.data.contentItems.first()

        // 2. Verify its properties are correct
        assertThat(newItem.title).isEqualTo("Test Ep")
        assertThat(newItem.videoId).isEqualTo("https://audio.mp3")
        assertThat(newItem.channelName).isEqualTo("My Podcast")
        assertThat(newItem.source).isEqualTo(ContentSource.GENERIC)
        assertThat(newItem.type).isEqualTo(ContentType.AUDIO)
        assertThat(newItem.podcastId).isEqualTo("pod1")
        assertThat(newItem.durationSeconds).isEqualTo(3600L)

        // 3. Verify the correct database effect is emitted
        assertThat(update.effects).hasSize(1)
        val dbEffect = update.effects.first()
        assertThat(dbEffect).isInstanceOf(GatekeeperEffect.DbUpsertContentItem::class.java)
        val upsertEffect = dbEffect as GatekeeperEffect.DbUpsertContentItem
        assertThat(upsertEffect.item).isEqualTo(newItem)
    }

    @Test
    fun testRefreshAllFeedsRequested_EmitsSyncAndRefreshEffects() {
        val action = GatekeeperAction.RefreshAllFeedsRequested
        val update = reduce(initialState, action)

        // Verify it emits the loading state action AND the command to actually refresh
        assertThat(
            update.effects.any {
                it is GatekeeperEffect.EmitAction && it.action is GatekeeperAction.PodcastSyncStarted
            },
        ).isTrue()
        assertThat(update.effects.any { it is GatekeeperEffect.SchedulePodcastRefresh }).isTrue()
    }

    @Test
    fun testSaveToContentBank_AppendsItemWithCorrectRankAndClearsLoading() {
        val stateWithLoading = initialState.copy(media = initialState.media.copy(isProcessingLink = true))
        val action =
            GatekeeperAction.SaveToContentBank(
                "vid1",
                "Test Title",
                ContentSource.YOUTUBE,
                ContentType.VIDEO,
                1000L,
                channelName = "Test Channel",
            )
        val update = reduce(stateWithLoading, action)
        val newState = update.state
        assertThat(newState.data.contentItems).hasSize(1)
        assertThat(
            newState.data.contentItems
                .first()
                .rank,
        ).isEqualTo(0L)
        assertThat(
            newState.data.contentItems
                .first()
                .channelName,
        ).isEqualTo("Test Channel")
        assertThat(newState.media.isProcessingLink).isFalse()

        assertThat(update.effects.any { it is GatekeeperEffect.DbUpsertContentItem }).isTrue()

        val action2 = GatekeeperAction.SaveToContentBank("vid2", "Test 2", ContentSource.YOUTUBE, ContentType.VIDEO, 2000L)
        val finalState = reduce(newState, action2).state
        assertThat(finalState.data.contentItems).hasSize(2)
        assertThat(finalState.data.contentItems[1].rank).isEqualTo(1L)
    }

    @Test
    fun testReorderContentBank_UpdatesRanks() {
        val item1 =
            ContentItem(
                id = "1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0L,
                capturedAtTimestamp = 1L,
            )
        val item2 =
            ContentItem(
                id = "2",
                videoId = "v2",
                title = "T2",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 1L,
                capturedAtTimestamp = 2L,
            )
        val item3 =
            ContentItem(
                id = "3",
                videoId = "v3",
                title = "T3",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 2L,
                capturedAtTimestamp = 3L,
            )
        val state = initialState.copy(data = initialState.data.copy(contentItems = listOf(item1, item2, item3)))
        val action = GatekeeperAction.ReorderContentBank(fromIndex = 0, toIndex = 2, currentTimestamp = 1000L)
        val newState = reduce(state, action).state
        assertThat(newState.data.contentItems[0].id).isEqualTo("2")
        assertThat(newState.data.contentItems[0].rank).isEqualTo(0L)
        assertThat(newState.data.contentItems[1].id).isEqualTo("3")
        assertThat(newState.data.contentItems[1].rank).isEqualTo(1L)
        assertThat(newState.data.contentItems[2].id).isEqualTo("1")
        assertThat(newState.data.contentItems[2].rank).isEqualTo(2L)
    }

    @Test
    fun testRemoveFromContentBank_RemovesItem() {
        val item1 =
            ContentItem(
                id = "1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0L,
                capturedAtTimestamp = 1L,
            )
        val state = initialState.copy(data = initialState.data.copy(contentItems = listOf(item1)))
        val update = reduce(state, GatekeeperAction.RemoveFromContentBank("1", 1000L))
        val newState = update.state
        assertThat(
            newState.data.contentItems
                .first()
                .isDeleted,
        ).isTrue()
        assertThat(update.effects.any { it is GatekeeperEffect.DbRemoveFromContentBank }).isTrue()
    }

    @Test
    fun testRemoteSyncCompleted_LastWriteWins_ResolvesConflicts() {
        val localVaultItem = VaultItem(id = "v1", query = "Local Query", capturedAtTimestamp = 1000L, lastModified = 1000L)
        val localContentItem =
            ContentItem(
                id = "c1",
                videoId = "vid1",
                title = "Local Title",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 1000L,
                lastModified = 3000L,
            )
        val stateWithLocal =
            initialState.copy(
                data = initialState.data.copy(vaultItems = listOf(localVaultItem), contentItems = listOf(localContentItem)),
            )
        val remoteVaultItem = VaultItem(id = "v1", query = "Remote Query", capturedAtTimestamp = 1000L, lastModified = 2000L)
        val remoteContentItem =
            ContentItem(
                id = "c1",
                videoId = "vid1",
                title = "Remote Title",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 1000L,
                lastModified = 1500L,
            )
        val action =
            GatekeeperAction.RemoteSyncCompleted(
                newVaultItems = listOf(remoteVaultItem),
                newContentItems = listOf(remoteContentItem),
            )
        val newState = reduce(stateWithLocal, action).state
        assertThat(newState.data.vaultItems).hasSize(1)
        assertThat(
            newState.data.vaultItems
                .first()
                .query,
        ).isEqualTo("Remote Query")
        assertThat(newState.data.contentItems).hasSize(1)
        assertThat(
            newState.data.contentItems
                .first()
                .title,
        ).isEqualTo("Local Title")
    }

    @Test
    fun testRemoteSyncCompleted_PreservesLocalDownloadStatus() {
        val localContentItem =
            ContentItem(
                id = "c1",
                videoId = "vid1",
                title = "Local Title",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 1000L,
                lastModified = 1000L,
                localFilePath = "/path/to/file",
                downloadStatus = DownloadStatus.COMPLETED,
            )
        val stateWithLocal = initialState.copy(data = initialState.data.copy(contentItems = listOf(localContentItem)))
        val remoteContentItem =
            ContentItem(
                id = "c1",
                videoId = "vid1",
                title = "Remote Title",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 1000L,
                lastModified = 2000L,
                localFilePath = null,
                downloadStatus = DownloadStatus.NONE,
            )
        val action = GatekeeperAction.RemoteSyncCompleted(emptyList(), listOf(remoteContentItem))
        val newState = reduce(stateWithLocal, action).state
        assertThat(newState.data.contentItems).hasSize(1)
        val item = newState.data.contentItems.first()
        assertThat(item.title).isEqualTo("Remote Title")
        assertThat(item.localFilePath).isEqualTo("/path/to/file")
        assertThat(item.downloadStatus).isEqualTo(DownloadStatus.COMPLETED)
    }

    @Test
    fun testUpdateSyncUrl_UpdatesState() {
        val action = GatekeeperAction.UpdateSyncUrl("http://127.0.0.1:8081")
        val newState = reduce(initialState, action).state
        assertThat(newState.sync.syncServerUrl).isEqualTo("http://127.0.0.1:8081")
    }

    @Test
    fun testLoginSuccess_SetsAuthState() {
        val action = GatekeeperAction.LoginSuccess("eyJ_MOCK_TOKEN")
        val newState = reduce(initialState, action).state
        assertThat(newState.sync.isAuthenticated).isTrue()
        assertThat(newState.sync.jwtToken).isEqualTo("eyJ_MOCK_TOKEN")
    }

    @Test
    fun testLogout_ClearsAuthState() {
        val authState = initialState.copy(sync = initialState.sync.copy(isAuthenticated = true, jwtToken = "eyJ_MOCK_TOKEN"))
        val newState = reduce(authState, GatekeeperAction.Logout).state
        assertThat(newState.sync.isAuthenticated).isFalse()
        assertThat(newState.sync.jwtToken).isNull()
    }

    @Test
    fun testWebEngineInitialized_setsReadyState() {
        val action = GatekeeperAction.WebEngineInitialized
        val newState = reduce(initialState, action).state
        assertThat(newState.media.isWebEngineReady).isTrue()
    }

    @Test
    fun testAddPinnedWebsite_AppendsToState() {
        val action = GatekeeperAction.AddPinnedWebsite("1", "My Site", "https://example.com")
        val newState = reduce(initialState, action).state
        assertThat(newState.data.missionControlWebsites).hasSize(1)
        assertThat(
            newState.data.missionControlWebsites
                .first()
                .label,
        ).isEqualTo("My Site")
    }

    @Test
    fun testRemovePinnedWebsite_RemovesFromState() {
        val site = PinnedWebsite("1", "My Site", "https://example.com")
        val state = initialState.copy(data = initialState.data.copy(missionControlWebsites = listOf(site)))
        val action = GatekeeperAction.RemovePinnedWebsite("1")
        val newState = reduce(state, action).state
        assertThat(newState.data.missionControlWebsites).isEmpty()
    }

    @Test
    fun testOpenPinnedWebsite_SetsUrl() {
        val action = GatekeeperAction.OpenPinnedWebsite("https://example.com")
        val newState = reduce(initialState, action).state
        assertThat(newState.media.activePinnedWebsiteUrl).isEqualTo("https://example.com")
    }

    @Test
    fun testClosePinnedWebsite_ClearsUrl() {
        val state = initialState.copy(media = initialState.media.copy(activePinnedWebsiteUrl = "https://example.com"))
        val action = GatekeeperAction.ClosePinnedWebsite
        val newState = reduce(state, action).state
        assertThat(newState.media.activePinnedWebsiteUrl).isNull()
    }

    @Test
    fun testSurgicalNavigationRequested_updatesUrl() {
        val url = "https://google.com"
        val action = GatekeeperAction.SurgicalNavigationRequested(url)
        val newState = reduce(initialState, action).state
        assertThat(newState.media.currentSurgicalUrl).isEqualTo(url)
    }

    @Test
    fun testAddAlternativeActivity_AppendsToState() {
        val action = GatekeeperAction.AddAlternativeActivity("Go for a walk", 1000L)
        val newState = reduce(initialState, action).state
        assertThat(newState.data.alternativeActivities).hasSize(1)
        assertThat(
            newState.data.alternativeActivities
                .first()
                .description,
        ).isEqualTo("Go for a walk")
    }

    @Test
    fun testRemoveAlternativeActivity_RemovesFromState() {
        val activity = AlternativeActivity("1", "Go for a walk", 1000L)
        val state = initialState.copy(data = initialState.data.copy(alternativeActivities = listOf(activity)))
        val action = GatekeeperAction.RemoveAlternativeActivity("1")
        val newState = reduce(state, action).state
        assertThat(newState.data.alternativeActivities).isEmpty()
    }

    @Test
    fun testOpenSurgicalFacebook_updatesUrl() {
        val url = "https://m.facebook.com/groups/123"
        val action = GatekeeperAction.OpenSurgicalFacebook(url)
        val newState = reduce(initialState, action).state
        assertThat(newState.media.activeFacebookUrl).isEqualTo(url)
    }

    @Test
    fun testCloseSurgicalFacebook_clearsUrl() {
        val activeState = initialState.copy(media = initialState.media.copy(activeFacebookUrl = "https://m.facebook.com/groups/"))
        val newState = reduce(activeState, GatekeeperAction.CloseSurgicalFacebook).state
        assertThat(newState.media.activeFacebookUrl).isNull()
    }

    @Test
    fun `GrantTemporaryCallWhitelist adds a 15-second whitelist`() {
        val action = GatekeeperAction.GrantTemporaryCallWhitelist(blacklistedApp, 100_000L)
        val newState = reduce(initialState, action).state

        val whitelist = newState.interception.activeWhitelists[blacklistedApp]
        assertThat(whitelist).isNotNull()
        assertThat(whitelist!!.reason).isEqualTo("INCOMING_CALL")
        assertThat(whitelist.expiresAtTimestamp).isEqualTo(100_000L + 15_000L)
    }

    @Test
    fun testExtractAndPlayMedia_setsExtractingMediaId() {
        val item =
            ContentItem(
                id = "c1",
                videoId = "vid1",
                title = "Title",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 0L,
            )
        val action = GatekeeperAction.ExtractAndPlayMedia(item)
        val newState = reduce(initialState, action).state
        assertThat(newState.media.extractingMediaId).isEqualTo("c1")
    }

    @Test
    fun testSaveMediaPosition_UpdatesSavedPositions() {
        val action = GatekeeperAction.SaveMediaPosition("testVideoId", 120.5f)
        val newState = reduce(initialState, action).state
        assertThat(newState.media.savedMediaPositions["testVideoId"]).isEqualTo(120.5f)
    }

    @Test
    fun testSaveMediaPosition_ResetsPositionToZero() {
        val stateWithPosition = initialState.copy(media = initialState.media.copy(savedMediaPositions = mapOf("testVideoId" to 120.5f)))
        val action = GatekeeperAction.SaveMediaPosition("testVideoId", 0f)
        val newState = reduce(stateWithPosition, action).state
        assertThat(newState.media.savedMediaPositions["testVideoId"]).isEqualTo(0f)
    }

    @Test
    fun testProcessPodcastUrl_SetsSyncingState() {
        val action = GatekeeperAction.ProcessPodcastUrl("https://example.com/feed.xml")
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.sync.isSyncingPodcasts).isTrue()
        assertThat(newState.sync.podcastSyncError).isNull()
        assertThat(update.effects.any { it is GatekeeperEffect.FetchPodcastFeedForSubscription }).isTrue()
    }

    @Test
    fun testPodcastSyncFailed_SetsErrorState() {
        val stateBefore = initialState.copy(sync = initialState.sync.copy(isSyncingPodcasts = true))
        val action = GatekeeperAction.PodcastSyncFailed("Network Error")
        val newState = reduce(stateBefore, action).state
        assertThat(newState.sync.isSyncingPodcasts).isFalse()
        assertThat(newState.sync.podcastSyncError).isEqualTo("Network Error")
    }

    @Test
    fun testPodcastSyncCompleted_ClearsSyncingState() {
        val stateBefore = initialState.copy(sync = initialState.sync.copy(isSyncingPodcasts = true, podcastSyncError = "Old Error"))
        val action = GatekeeperAction.PodcastSyncCompleted
        val newState = reduce(stateBefore, action).state
        assertThat(newState.sync.isSyncingPodcasts).isFalse()
        assertThat(newState.sync.podcastSyncError).isNull()
    }

    @Test
    fun testRemovePodcastSubscription_RemovesSubscriptionAndMarksContentAsDeleted() {
        val sub = PodcastSubscription(id = "sub1", feedUrl = "url", showTitle = "Title", artworkUrl = null)
        val content =
            ContentItem(
                id = "c1",
                podcastId = "sub1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val slot = IntentionalSlotItem(slotIndex = 0, contentItem = content)
        val stateBefore =
            initialState.copy(
                sync = initialState.sync.copy(podcastSubscriptions = listOf(sub)),
                data = initialState.data.copy(contentItems = listOf(content), intentionalSlots = listOf(slot)),
            )
        val action = GatekeeperAction.RemovePodcastSubscription("sub1", 1000L)
        val newState = reduce(stateBefore, action).state
        assertThat(newState.sync.podcastSubscriptions).isEmpty()
        assertThat(
            newState.data.contentItems
                .first()
                .isDeleted,
        ).isTrue()
        assertThat(
            newState.data.contentItems
                .first()
                .lastModified,
        ).isEqualTo(1000L)
        assertThat(newState.data.intentionalSlots).isEmpty()
    }

    @Test
    fun testSavePodcastSubscription_AddsSubscription() {
        val sub = PodcastSubscription(id = "sub1", feedUrl = "url", showTitle = "Title", artworkUrl = null)
        val action = GatekeeperAction.SavePodcastSubscription(sub)
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.sync.podcastSubscriptions).hasSize(1)
        assertThat(
            newState.sync.podcastSubscriptions
                .first()
                .id,
        ).isEqualTo("sub1")
        assertThat(update.effects.any { it is GatekeeperEffect.DbInsertPodcastSubscription }).isTrue()
    }

    @Test
    fun testLoadPodcastEpisodes_setsLoading() {
        val action = GatekeeperAction.LoadPodcastEpisodes("url", "podcast1")
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.media.isLoadingEpisodes).isTrue()
        assertThat(newState.media.activePodcastId).isEqualTo("podcast1")
        assertThat(update.effects.any { it is GatekeeperEffect.FetchPodcastFeedForEpisodes }).isTrue()
    }

    @Test
    fun testPodcastEpisodesLoaded_setsActiveEpisodes() {
        val stateWithLoading = initialState.copy(media = initialState.media.copy(isLoadingEpisodes = true))
        val mockEpisodes =
            listOf(
                CachedEpisode(
                    title = "Ep 1",
                    audioUrl = "audio_url",
                    durationSeconds = 1000L,
                    pubDate = "2023-01-01",
                    podcastId = "podcast1",
                ),
            )
        val action = GatekeeperAction.PodcastEpisodesLoaded(mockEpisodes, "podcast1")
        val newState = reduce(stateWithLoading, action).state
        assertThat(newState.media.isLoadingEpisodes).isFalse()
        assertThat(newState.media.activePodcastEpisodes).isEqualTo(mockEpisodes)
        assertThat(newState.media.activePodcastId).isEqualTo("podcast1")
    }

    @Test
    fun testClearPodcastEpisodes_clearsActive() {
        val stateWithActive =
            initialState.copy(
                media = initialState.media.copy(isLoadingEpisodes = true, activePodcastEpisodes = listOf(), activePodcastId = "podcast1"),
            )
        val newState = reduce(stateWithActive, GatekeeperAction.ClearPodcastEpisodes).state
        assertThat(newState.media.isLoadingEpisodes).isFalse()
        assertThat(newState.media.activePodcastEpisodes).isNull()
        assertThat(newState.media.activePodcastId).isNull()
    }

    @Test
    fun testLoadLatestGlobalEpisodes_setsLoading() {
        val action = GatekeeperAction.LoadLatestGlobalEpisodes
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.media.isLoadingGlobalEpisodes).isTrue()
        assertThat(update.effects.any { it is GatekeeperEffect.DbLoadLatestGlobalEpisodes }).isTrue()
    }

    @Test
    fun testLatestGlobalEpisodesLoaded_setsActiveEpisodes() {
        val stateWithLoading = initialState.copy(media = initialState.media.copy(isLoadingGlobalEpisodes = true))
        val mockUnified =
            listOf(
                UnifiedEpisode(
                    id = "ep_1",
                    podcastId = "podcast1",
                    title = "Global Episode 1",
                    audioUrl = "https://example.com/global1.mp3",
                    durationSeconds = 1800L,
                    pubDate = "Feb 01",
                    lastModified = 0L,
                    showTitle = "The Sovereign Podcast",
                    artworkUrl = null,
                ),
            )
        val action = GatekeeperAction.LatestGlobalEpisodesLoaded(mockUnified)
        val newState = reduce(stateWithLoading, action).state
        assertThat(newState.media.isLoadingGlobalEpisodes).isFalse()
        assertThat(newState.media.latestGlobalEpisodes).isEqualTo(mockUnified)
    }

    @Test
    fun testCacheParsedEpisodes_leavesStateUnchanged() {
        val action = GatekeeperAction.CacheParsedEpisodes(emptyList(), "podcast1")
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState).isEqualTo(initialState)
        assertThat(update.effects.any { it is GatekeeperEffect.DbCacheParsedEpisodes }).isTrue()
    }

    @Test
    fun testOpenNativePlayer_SetsActiveNativeMediaItem() {
        val ep =
            ContentItem(
                id = "ep1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val action = GatekeeperAction.OpenNativePlayer(ep)
        val newState = reduce(initialState, action).state
        assertThat(newState.media.activeNativeMediaItem).isEqualTo(ep)
    }

    @Test
    fun testCloseNativePlayer_ClearsActiveNativeMediaItem() {
        val ep =
            ContentItem(
                id = "ep1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val activeState = initialState.copy(media = initialState.media.copy(activeNativeMediaItem = ep))
        val action = GatekeeperAction.CloseNativePlayer
        val newState = reduce(activeState, action).state
        assertThat(newState.media.activeNativeMediaItem).isNull()
    }

    @Test
    fun testSaveIntentionalSlot_AppendsAndReplacesSlot() {
        val mockContent1 =
            ContentItem(
                id = "c1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val mockContent2 =
            ContentItem(
                id = "c2",
                videoId = "v2",
                title = "T2",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 1,
                capturedAtTimestamp = 0,
            )
        val action1 = GatekeeperAction.SaveIntentionalSlot(slotIndex = 0, contentItem = mockContent1)
        val update1 = reduce(initialState, action1)
        val state1 = update1.state
        assertThat(state1.data.intentionalSlots).hasSize(1)
        assertThat(
            state1.data.intentionalSlots
                .first()
                .contentItem.id,
        ).isEqualTo("c1")
        assertThat(update1.effects.any { it is GatekeeperEffect.DbInsertIntentionalSlot }).isTrue()

        val action2 = GatekeeperAction.SaveIntentionalSlot(slotIndex = 0, contentItem = mockContent2)
        val state2 = reduce(state1, action2).state
        assertThat(state2.data.intentionalSlots).hasSize(1)
        assertThat(
            state2.data.intentionalSlots
                .first()
                .contentItem.id,
        ).isEqualTo("c2")
    }

    @Test
    fun testClearIntentionalSlot_RemovesItem() {
        val mockContent =
            ContentItem(
                id = "c1",
                videoId = "v1",
                title = "T1",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val state1 = reduce(initialState, GatekeeperAction.SaveIntentionalSlot(slotIndex = 2, contentItem = mockContent)).state
        val update = reduce(state1, GatekeeperAction.ClearIntentionalSlot(slotIndex = 2))
        val state2 = update.state
        assertThat(state2.data.intentionalSlots).isEmpty()
        assertThat(update.effects.any { it is GatekeeperEffect.DbClearIntentionalSlot }).isTrue()
    }

    @Test
    fun testSetManualLockdown_UpdatesState() {
        val action = GatekeeperAction.SetManualLockdown(true)
        val newState = reduce(initialState, action).state
        assertThat(newState.interception.isManualLockdownActive).isTrue()
    }

    @Test
    fun testUpdateGroupCombinator_UpdatesState() {
        val action = GatekeeperAction.UpdateGroupCombinator("group1", RuleCombinator.ALL)
        val newState = reduce(initialState, action).state
        assertThat(
            newState.interception.appGroups
                .first()
                .combinator,
        ).isEqualTo(RuleCombinator.ALL)
    }

    @Test
    fun testUpdateGroupApps_UpdatesState() {
        val action = GatekeeperAction.UpdateGroupApps("group1", setOf("com.new.app1", "com.new.app2"))
        val newState = reduce(initialState, action).state
        assertThat(
            newState.interception.appGroups
                .first()
                .apps,
        ).containsExactly("com.new.app1", "com.new.app2")
    }

    @Test
    fun testUpdateGroupApps_ToEmpty_UpdatesState() {
        val action = GatekeeperAction.UpdateGroupApps("group1", emptySet())
        val newState = reduce(initialState, action).state
        assertThat(
            newState.interception.appGroups
                .first()
                .apps,
        ).isEmpty()
    }

    @Test
    fun testAddDomainBlockRule_AppendsRule() {
        val action =
            GatekeeperAction.AddDomainBlockRule(
                id = "domainRule1",
                groupId = "group1",
                domains = setOf("reddit.com", "youtube.com"),
            )
        val newState = reduce(initialState, action).state
        assertThat(
            newState.interception.appGroups
                .first()
                .rules,
        ).hasSize(1)
        val rule =
            newState.interception.appGroups
                .first()
                .rules
                .first()
        assertThat(rule).isInstanceOf(BlockingRule.DomainBlock::class.java)
        assertThat((rule as BlockingRule.DomainBlock).domains).containsExactly("reddit.com", "youtube.com")
    }

    @Test
    fun testUpdateDomainBlockRule_UpdatesDomains() {
        val initialAction = GatekeeperAction.AddDomainBlockRule("domainRule1", "group1", setOf("reddit.com"))
        val stateWithRule = reduce(initialState, initialAction).state
        val updateAction = GatekeeperAction.UpdateDomainBlockRule("domainRule1", "group1", setOf("reddit.com", "news.ycombinator.com"))
        val newState = reduce(stateWithRule, updateAction).state
        val rule =
            newState.interception.appGroups
                .first()
                .rules
                .first() as BlockingRule.DomainBlock
        assertThat(rule.domains).containsExactly("reddit.com", "news.ycombinator.com")
    }

    @Test
    fun testAddAlwaysBlockRule_appendsRuleToGroup() {
        val action = GatekeeperAction.AddAlwaysBlockRule("alwaysBlock1", "group1")
        val newState = reduce(initialState, action).state
        val group = newState.interception.appGroups.find { it.id == "group1" }
        assertThat(group?.rules).hasSize(1)
        assertThat(group?.rules?.first()).isInstanceOf(BlockingRule.AlwaysBlock::class.java)
    }

    @Test
    fun testAddCheckInRule_AppendsRule() {
        val action =
            GatekeeperAction.AddCheckInRule(
                id = "rule1",
                groupId = "group1",
                checkInTimesMinutes = listOf(600),
                durationMinutes = 15,
                daysOfWeek = DayOfWeek.values().toSet(),
            )
        val newState = reduce(initialState, action).state
        assertThat(
            newState.interception.appGroups
                .first()
                .rules,
        ).hasSize(1)
        assertThat(
            newState.interception.appGroups
                .first()
                .rules
                .first(),
        ).isInstanceOf(BlockingRule.CheckIn::class.java)
    }

    @Test
    fun testUpdateCheckInRule_UpdatesRule() {
        val addAction =
            GatekeeperAction.AddCheckInRule(
                id = "rule1",
                groupId = "group1",
                checkInTimesMinutes = listOf(600),
                durationMinutes = 15,
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            )
        val stateWithRule = reduce(initialState, addAction).state
        val updateAction =
            GatekeeperAction.UpdateCheckInRule(
                id = "rule1",
                groupId = "group1",
                checkInTimesMinutes = listOf(600, 720),
                durationMinutes = 30,
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            )
        val newState = reduce(stateWithRule, updateAction).state
        val rule =
            newState.interception.appGroups
                .first()
                .rules
                .first() as BlockingRule.CheckIn
        assertThat(rule.checkInTimesMinutes).containsExactly(600, 720)
        assertThat(rule.durationMinutes).isEqualTo(30)
        assertThat(rule.daysOfWeek).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
    }

    @Test
    fun testRedeemCheckInToken_AddsWhitelistAndConsumedLog() {
        val action =
            GatekeeperAction.RedeemCheckInToken(
                groupId = "group1",
                checkInTimeMinutes = 600,
                durationMinutes = 15,
                reason = "Needed an unblock",
                currentTimestamp = 1000L,
            )
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.data.consumedCheckIns).hasSize(1)
        assertThat(
            newState.data.consumedCheckIns
                .first()
                .timeMinutes,
        ).isEqualTo(600)
        assertThat(newState.interception.activeWhitelists).containsKey(blacklistedApp)
        assertThat(newState.interception.activeWhitelists[blacklistedApp]!!.reason).isEqualTo("Needed an unblock")
        assertThat(newState.interception.activeWhitelists[blacklistedApp]!!.allocatedDurationMillis).isEqualTo(900_000L)
        assertThat(update.effects.any { it is GatekeeperEffect.DbRedeemCheckInToken }).isTrue()
    }

    @Test
    fun testEndGroupSession_RemovesWhitelist() {
        val stateWithWhitelist =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        activeWhitelists =
                            mapOf(blacklistedApp to TemporaryWhitelist(blacklistedApp, "Test", 0L, 1000L, 1000L)),
                    ),
            )
        val action = GatekeeperAction.EndGroupSession("group1")
        val newState = reduce(stateWithWhitelist, action).state
        assertThat(newState.interception.activeWhitelists).isEmpty()
    }

    @Test
    fun testEndAppSession_RemovesWhitelistAndClearsExitInterview() {
        val stateWithWhitelistAndOverlay =
            initialState.copy(
                interception =
                    initialState.interception.copy(
                        activeWhitelists =
                            mapOf(blacklistedApp to TemporaryWhitelist(blacklistedApp, "Test", 0L, 1000L, 1000L)),
                        isOverlayActive = true,
                        pendingExitInterview = blacklistedApp,
                    ),
            )
        val action = GatekeeperAction.EndAppSession(blacklistedApp)
        val newState = reduce(stateWithWhitelistAndOverlay, action).state
        assertThat(newState.interception.activeWhitelists).isEmpty()
        assertThat(newState.interception.isOverlayActive).isFalse()
        assertThat(newState.interception.pendingExitInterview).isNull()
    }

    @Test
    fun testTriggerExitInterview_SetsOverlayAndPendingApp() {
        val action = GatekeeperAction.TriggerExitInterview(blacklistedApp)
        val newState = reduce(initialState, action).state
        assertThat(newState.interception.isOverlayActive).isTrue()
        assertThat(newState.interception.pendingExitInterview).isEqualTo(blacklistedApp)
    }

    @Test
    fun testCancelExitInterview_ClearsOverlayAndPendingApp() {
        val stateWithInterview =
            initialState.copy(
                interception = initialState.interception.copy(isOverlayActive = true, pendingExitInterview = blacklistedApp),
            )
        val action = GatekeeperAction.CancelExitInterview
        val newState = reduce(stateWithInterview, action).state
        assertThat(newState.interception.isOverlayActive).isFalse()
        assertThat(newState.interception.pendingExitInterview).isNull()
    }

    @Test
    fun testClearExportData_ClearsState() {
        val stateWithData = initialState.copy(data = initialState.data.copy(exportData = "Some data"))
        val newState = reduce(stateWithData, GatekeeperAction.ClearExportData).state
        assertThat(newState.data.exportData).isNull()
    }

    @Test
    fun testFrictionCompleted_IncrementsBypassCounter() {
        val action = GatekeeperAction.FrictionCompleted("com.test", 900_000L, 1000L)
        val newState = reduce(initialState, action).state
        assertThat(newState.data.analyticsBypasses).isEqualTo(1)
    }

    @Test
    fun testGenerateExportData_UpdatesState() {
        val newState = reduce(initialState, GatekeeperAction.UpgradeToProTier).state
        assertThat(newState.sync.isProTier).isTrue()
    }

    @Test
    fun testSetFrictionGame_UpdatesState() {
        val action = GatekeeperAction.SetFrictionGame(FrictionGame.HOLD_STEADY)
        val newState = reduce(initialState, action).state
        assertThat(newState.interception.activeFrictionGame).isEqualTo(FrictionGame.HOLD_STEADY)
    }

    @Test
    fun testUpdateMissionControlApps_UpdatesState() {
        val apps = listOf("com.example.app1", "com.example.app2")
        val action = GatekeeperAction.UpdateMissionControlApps(apps)
        val newState = reduce(initialState, action).state
        assertThat(newState.data.missionControlApps).isEqualTo(apps)
    }

    @Test
    fun testUpdatePhaseWindows_UpdatesState() {
        val action = GatekeeperAction.UpdatePhaseWindows(100, 200, 300, 400)
        val newState = reduce(initialState, action).state
        assertThat(newState.data.deepWorkStartMinutes).isEqualTo(100)
        assertThat(newState.data.deepWorkEndMinutes).isEqualTo(200)
        assertThat(newState.data.gatheringStartMinutes).isEqualTo(300)
        assertThat(newState.data.gatheringEndMinutes).isEqualTo(400)
    }

    @Test
    fun testLogSessionMetacognition_AppendsToSessionLogs() {
        val action =
            GatekeeperAction.LogSessionMetacognition(
                packageName = "CleanPlayer: YouTube",
                durationMillis = 120000L,
                emotion = Emotion.HAPPY,
                currentTimestamp = 12345L,
            )
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.data.sessionLogs).hasSize(1)
        val log = newState.data.sessionLogs.first()
        assertThat(log.packageName).isEqualTo("CleanPlayer: YouTube")
        assertThat(log.durationMillis).isEqualTo(120000L)
        assertThat(log.emotion).isEqualTo(Emotion.HAPPY)
        assertThat(update.effects.any { it is GatekeeperEffect.DbInsertSessionLog && it.log == log }).isTrue()
    }

    @Test
    fun testPermissionsUpdated_setsFlagsAndCalculatesDualMoatStatus() {
        val action =
            GatekeeperAction.PermissionsUpdated(
                hasOverlay = true,
                hasUsageAccess = true,
                hasAccessibility = false,
                hasNotificationAccess = false,
                isBatteryDisabled = false,
            )
        val partialState = reduce(initialState, action).state
        assertThat(partialState.interception.hasOverlayPermission).isTrue()
        assertThat(partialState.interception.hasUsageAccessPermission).isTrue()
        assertThat(partialState.interception.hasAccessibilityPermission).isFalse()
        assertThat(partialState.interception.hasNotificationAccessPermission).isFalse()
        assertThat(partialState.interception.isBatteryOptimizationDisabled).isFalse()
        assertThat(partialState.isDualMoatEnabled).isFalse()
        val fullAction = action.copy(hasAccessibility = true, hasNotificationAccess = true, isBatteryDisabled = true)
        val finalState = reduce(initialState, fullAction).state
        assertThat(finalState.isDualMoatEnabled).isTrue()
    }

    @Test
    fun testDownloadMediaRequested_SetsStatusToQueued() {
        val item =
            ContentItem(
                id = "1",
                videoId = "url",
                title = "T",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val state = initialState.copy(data = initialState.data.copy(contentItems = listOf(item)))
        val update = reduce(state, GatekeeperAction.DownloadMediaRequested("1"))
        val newState = update.state
        assertThat(
            newState.data.contentItems
                .first()
                .downloadStatus,
        ).isEqualTo(DownloadStatus.QUEUED)
        assertThat(update.effects.any { it is GatekeeperEffect.DownloadMedia && it.id == "1" }).isTrue()
    }

    @Test
    fun testDownloadProgressUpdated_SetsStatusToDownloadingAndUpdatesProgress() {
        val item =
            ContentItem(
                id = "1",
                videoId = "url",
                title = "T",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val state = initialState.copy(data = initialState.data.copy(contentItems = listOf(item)))
        val newState = reduce(state, GatekeeperAction.DownloadProgressUpdated("1", 45.5f)).state
        assertThat(
            newState.data.contentItems
                .first()
                .downloadStatus,
        ).isEqualTo(DownloadStatus.DOWNLOADING)
        assertThat(newState.media.activeDownloads["1"]).isEqualTo(45.5f)
    }

    @Test
    fun testDownloadCompleted_SetsStatusToCompletedAndPath() {
        val item =
            ContentItem(
                id = "1",
                videoId = "url",
                title = "T",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
            )
        val state =
            initialState.copy(
                data = initialState.data.copy(contentItems = listOf(item)),
                media =
                    initialState.media.copy(
                        activeDownloads =
                            mapOf("1" to 100f),
                    ),
            )
        val update = reduce(state, GatekeeperAction.DownloadCompleted("1", "/path/to/file.mp3"))
        val newState = update.state
        assertThat(
            newState.data.contentItems
                .first()
                .downloadStatus,
        ).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(
            newState.data.contentItems
                .first()
                .localFilePath,
        ).isEqualTo("/path/to/file.mp3")
        assertThat(newState.media.activeDownloads).isEmpty()
        assertThat(update.effects.any { it is GatekeeperEffect.DbUpdateDownloadStatus && it.id == "1" }).isTrue()
    }

    @Test
    fun testDeleteDownloadedMedia_ResetsStatusAndPath() {
        val item =
            ContentItem(
                id = "1",
                videoId = "url",
                title = "T",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0,
                downloadStatus = DownloadStatus.COMPLETED,
                localFilePath = "/path/to/file.mp3",
            )
        val state = initialState.copy(data = initialState.data.copy(contentItems = listOf(item)))
        val update = reduce(state, GatekeeperAction.DeleteDownloadedMedia("1"))
        val newState = update.state
        assertThat(
            newState.data.contentItems
                .first()
                .downloadStatus,
        ).isEqualTo(DownloadStatus.NONE)
        assertThat(
            newState.data.contentItems
                .first()
                .localFilePath,
        ).isNull()
        assertThat(update.effects.any { it is GatekeeperEffect.DeleteDownloadedMedia && it.id == "1" }).isTrue()
    }

    @Test
    fun testSearchPodcastsRequested_SetsLoadingState() {
        val update = reduce(initialState, GatekeeperAction.SearchPodcastsRequested("huberman"))
        val newState = update.state
        assertThat(newState.media.isSearchingPodcasts).isTrue()
        assertThat(newState.media.podcastSearchResults).isEmpty()
        assertThat(update.effects.any { it is GatekeeperEffect.SearchPodcasts && it.query == "huberman" }).isTrue()
    }

    @Test
    fun testPodcastSearchCompleted_SetsResultsAndClearsLoading() {
        val state = initialState.copy(media = initialState.media.copy(isSearchingPodcasts = true))
        val mockResults =
            listOf(
                com.aegisgatekeeper.app.api
                    .PodcastFeedDto(id = 1L, title = "Huberman Lab", url = "https://feed.xml"),
            )
        val newState = reduce(state, GatekeeperAction.PodcastSearchCompleted(mockResults)).state
        assertThat(newState.media.isSearchingPodcasts).isFalse()
        assertThat(newState.media.podcastSearchResults).hasSize(1)
        assertThat(
            newState.media.podcastSearchResults
                .first()
                .title,
        ).isEqualTo("Huberman Lab")
    }

    @Test
    fun testNotificationIntercepted_AppendsToDigest() {
        val action =
            GatekeeperAction.NotificationIntercepted(
                packageName = "com.whatsapp",
                title = "WhatsApp: John Doe",
                content = "Hello there!",
                timestamp = 1000L,
            )
        val update = reduce(initialState, action)
        val newState = update.state
        assertThat(newState.sync.notificationDigest).hasSize(1)
        assertThat(
            newState.sync.notificationDigest
                .first()
                .packageName,
        ).isEqualTo("com.whatsapp")
        assertThat(
            newState.sync.notificationDigest
                .first()
                .title,
        ).isEqualTo("WhatsApp: John Doe")
        assertThat(update.effects.any { it is GatekeeperEffect.DbInsertNotificationDigest }).isTrue()
    }

    @Test
    fun testBeeperSyncActions_UpdateState() {
        val reqStateUpdate = reduce(initialState, GatekeeperAction.RequestBeeperSync)
        val reqState = reqStateUpdate.state
        assertThat(reqState.sync.isSyncingBeeper).isTrue()
        assertThat(reqStateUpdate.effects.any { it is GatekeeperEffect.SyncBeeperChats }).isTrue()

        val loadedState = reduce(reqState, GatekeeperAction.BeeperChatsLoaded(listOf(BeeperChat("1", "Test", "WhatsApp")))).state
        assertThat(loadedState.sync.isSyncingBeeper).isFalse()
        assertThat(loadedState.sync.beeperChats).hasSize(1)

        val failedState = reduce(reqState, GatekeeperAction.BeeperSyncFailed("Error")).state
        assertThat(failedState.sync.isSyncingBeeper).isFalse()
    }

    @Test
    fun testScheduleMessage_AppendsToState() {
        val msg = ScheduledMessage("1", "room1", "Test Chat", "Hello", 1000L)
        val update = reduce(initialState, GatekeeperAction.ScheduleMessage(msg))
        val newState = update.state
        assertThat(newState.sync.scheduledMessages).hasSize(1)
        assertThat(update.effects.any { it is GatekeeperEffect.DbInsertScheduledMessage && it.message == msg }).isTrue()
    }

    @Test
    fun testMessageStatusUpdates_ModifiesState() {
        val msg = ScheduledMessage("1", "room1", "Test Chat", "Hello", 1000L)
        val stateWithMessage = reduce(initialState, GatekeeperAction.ScheduleMessage(msg)).state

        val cancelledStateUpdate = reduce(stateWithMessage, GatekeeperAction.CancelScheduledMessage("1"))
        val cancelledState = cancelledStateUpdate.state
        assertThat(
            cancelledState.sync.scheduledMessages
                .first()
                .status,
        ).isEqualTo(MessageStatus.CANCELLED)
        assertThat(cancelledStateUpdate.effects.any { it is GatekeeperEffect.DbUpdateScheduledMessageStatus }).isTrue()

        val sentState = reduce(stateWithMessage, GatekeeperAction.MessageDelivered("1")).state
        assertThat(
            sentState.sync.scheduledMessages
                .first()
                .status,
        ).isEqualTo(MessageStatus.SENT)

        val failedState = reduce(stateWithMessage, GatekeeperAction.MessageFailed("1", "Error")).state
        assertThat(
            failedState.sync.scheduledMessages
                .first()
                .status,
        ).isEqualTo(MessageStatus.FAILED)
    }

        @Test
    fun testUpdateFilterRules_UpdatesStateAndEmitsCompileEffect() {
        val rules = listOf("||new-tracker.com^", "example.com##.ad")
        val hash = "mockhash123"
        val action = GatekeeperAction.UpdateFilterRules(rules, hash)
        val update = reduce(initialState, action)
        val newState = update.state

        assertThat(newState.media.declarativeFilterRules).isEqualTo(rules)
        assertThat(newState.media.filterRulesHash).isEqualTo(hash)
        assertThat(update.effects.any { it is GatekeeperEffect.CompileFilterRules && it.rules == rules }).isTrue()
    }

    @Test
    fun testInitialStateLoaded_ReplacesState() {
        val loadedState =
            GatekeeperState(
                sync = SyncAndIntegrationState(isProTier = true, jwtToken = "mock_token"),
                data = DataState(missionControlApps = listOf("com.test.app")),
            )
        val action = GatekeeperAction.InitialStateLoaded(loadedState)
        val newState = reduce(initialState, action).state
        assertThat(newState).isEqualTo(loadedState)
        assertThat(newState.sync.isProTier).isTrue()
        assertThat(newState.sync.jwtToken).isEqualTo("mock_token")
        assertThat(newState.data.missionControlApps).containsExactly("com.test.app")
    }
}
