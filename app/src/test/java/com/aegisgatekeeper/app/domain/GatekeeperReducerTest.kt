package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.api.ItemId
import com.aegisgatekeeper.app.api.ThumbnailInfo
import com.aegisgatekeeper.app.api.Thumbnails
import com.aegisgatekeeper.app.api.YoutubeSearchItem
import com.aegisgatekeeper.app.api.YoutubeSnippet
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
            appGroups = listOf(AppGroup(id = "group1", name = "Test", apps = setOf(blacklistedApp), combinator = RuleCombinator.ANY)),
        )

    @Test
    fun testRuleViolationDetected_NoWhitelist_TriggersOverlay() {
        // Arrange: The app is blacklisted and has no active whitelist.
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)

        // Act: We run the reducer.
        val newState = reduce(initialState, action)

        // Assert: The overlay should now be active for the intercepted app.
        assertThat(newState.isOverlayActive).isTrue()
        assertThat(newState.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        assertThat(newState.activeBlockReason).isEqualTo("Limit Reached")
    }

    @Test
    fun testAppBroughtToForeground_NotBlacklisted_DoesNothing() {
        // Arrange: The app is not on our blacklist.
        val action = GatekeeperAction.AppBroughtToForeground(whitelistedApp, 1000L)

        // Act
        val newState = reduce(initialState, action)

        // Assert: The state should be completely unchanged, except for tracking the active app.
        assertThat(newState).isEqualTo(initialState.copy(activeForegroundApp = whitelistedApp))
    }

    @Test
    fun testRuleViolationDetected_Whitelisted_DoesNothing() {
        // Arrange: The app is blacklisted, but has a valid whitelist.
        val stateWithWhitelist =
            initialState.copy(
                activeWhitelists =
                    mapOf(
                        blacklistedApp to
                            TemporaryWhitelist(
                                packageName = blacklistedApp,
                                reason = "Test",
                                grantedAtTimestamp = 500L,
                                expiresAtTimestamp = 1500L, // Expires in the future
                                allocatedDurationMillis = 300_000L,
                            ),
                    ),
            )
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)

        // Act
        val newState = reduce(stateWithWhitelist, action)

        // Assert: The state is unchanged; the user is allowed through.
        assertThat(newState).isEqualTo(stateWithWhitelist.copy(activeForegroundApp = blacklistedApp))
    }

    @Test
    fun testRuleViolationDetected_ExpiredWhitelist_TriggersOverlay() {
        // Arrange: The whitelist's expiry timestamp is in the past.
        val stateWithExpiredWhitelist =
            initialState.copy(
                activeWhitelists =
                    mapOf(
                        blacklistedApp to
                            TemporaryWhitelist(
                                packageName = blacklistedApp,
                                reason = "Test",
                                grantedAtTimestamp = 100L,
                                expiresAtTimestamp = 500L, // Expired
                                allocatedDurationMillis = 300_000L,
                            ),
                    ),
            )
        val action = GatekeeperAction.RuleViolationDetected(blacklistedApp, "Limit Reached", 1000L)

        // Act
        val newState = reduce(stateWithExpiredWhitelist, action)

        // Assert: The overlay is triggered because the whitelist is no longer valid.
        assertThat(newState.isOverlayActive).isTrue()
        assertThat(newState.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        // Must capture the duration to show the Swap UI
        assertThat(newState.expiredSessionDurationMillis).isEqualTo(300_000L)
        assertThat(newState.activeBlockReason).isEqualTo("Limit Reached")
    }

    @Test
    fun testSessionExpired_TriggersOverlayAndSetsExpiredDuration() {
        // Arrange: A session is currently active.
        val activeState =
            initialState.copy(
                activeWhitelists =
                    mapOf(
                        blacklistedApp to TemporaryWhitelist(blacklistedApp, null, 0L, 1000L, 600_000L),
                    ),
            )

        // Act: The foreground service triggers an expiration
        val action = GatekeeperAction.SessionExpired(blacklistedApp, 600_000L)
        val newState = reduce(activeState, action)

        // Assert
        assertThat(newState.isOverlayActive).isTrue()
        assertThat(newState.currentlyInterceptedApp).isEqualTo(blacklistedApp)
        assertThat(newState.expiredSessionDurationMillis).isEqualTo(600_000L)
        assertThat(newState.activeWhitelists).isEmpty() // Whitelist is stripped
    }

    @Test
    fun testEmergencyBypassRequested_GrantsWhitelist_DismissesOverlay() {
        // Arrange: The overlay is currently active.
        val stateWithOverlay =
            initialState.copy(
                isOverlayActive = true,
                currentlyInterceptedApp = blacklistedApp,
            )
        val action =
            GatekeeperAction.EmergencyBypassRequested(
                packageName = blacklistedApp,
                reason = "I need to call an Uber",
                allocatedDurationMillis = 300_000L,
                currentTimestamp = 1000L,
            )

        // Act
        val newState = reduce(stateWithOverlay, action)

        // Assert: The overlay is dismissed and a whitelist is created.
        assertThat(newState.isOverlayActive).isFalse()
        assertThat(newState.currentlyInterceptedApp).isNull()
        assertThat(newState.activeWhitelists).containsKey(blacklistedApp)
        val whitelist = newState.activeWhitelists[blacklistedApp]!!
        assertThat(whitelist.reason).isEqualTo("I need to call an Uber")
        // 5-minute (300,000 ms) grant
        assertThat(whitelist.expiresAtTimestamp).isEqualTo(1000L + 300_000L)
        // Verify analytics tracking
        assertThat(newState.analyticsBypasses).isEqualTo(1)
    }

    @Test
    fun testDismissOverlay_ClearsState() {
        // Arrange
        val stateWithOverlay =
            initialState.copy(
                isOverlayActive = true,
                currentlyInterceptedApp = blacklistedApp,
            )

        // Act
        val newState = reduce(stateWithOverlay, GatekeeperAction.DismissOverlay)

        // Assert
        assertThat(newState.isOverlayActive).isFalse()
        assertThat(newState.currentlyInterceptedApp).isNull()
    }

    @Test
    fun testMarkVaultItemResolved_UpdatesItemStatus() {
        // Arrange
        val item = VaultItem(id = "123", query = "Test Query", capturedAtTimestamp = 1000L)
        val stateWithItem = initialState.copy(vaultItems = listOf(item))
        val action = GatekeeperAction.MarkVaultItemResolved("123", 1000L)

        // Act
        val newState = reduce(stateWithItem, action)

        // Assert
        assertThat(newState.vaultItems).hasSize(1)
        assertThat(newState.vaultItems.first().isResolved).isTrue()
    }

    @Test
    fun testSaveToVault_AppendsToVaultItems() {
        // Arrange
        val action =
            GatekeeperAction.SaveToVault(
                query = "best standing desks",
                currentTimestamp = 2000L,
            )

        // Act
        val newState = reduce(initialState, action)

        // Assert
        assertThat(newState.vaultItems).hasSize(1)

        val savedItem = newState.vaultItems.first()
        assertThat(savedItem.query).isEqualTo("best standing desks")
        assertThat(savedItem.capturedAtTimestamp).isEqualTo(2000L)
        assertThat(savedItem.isResolved).isFalse()
    }

    @Test
    fun testSetCustomInterceptionMessage_UpdatesMap() {
        val action = GatekeeperAction.SetCustomInterceptionMessage("com.reddit.frontpage", "Read a book instead.")
        val newState = reduce(initialState, action)
        assertThat(newState.customMessages["com.reddit.frontpage"]).isEqualTo("Read a book instead.")
    }

    @Test
    fun testRemoveCustomInterceptionMessage_RemovesFromMap() {
        val stateWithMsg = initialState.copy(customMessages = mapOf("com.reddit.frontpage" to "Read a book instead."))
        val action = GatekeeperAction.RemoveCustomInterceptionMessage("com.reddit.frontpage")
        val newState = reduce(stateWithMsg, action)
        assertThat(newState.customMessages).isEmpty()
    }

    // --- Content Bank Reducer Tests ---

    @Test
    fun testProcessSharedLink_SetsIsProcessingLinkToTrue() {
        val action = GatekeeperAction.ProcessSharedLink(url = "https://youtu.be/dQw4w9WgXcQ", currentTimestamp = 1000L)
        val newState = reduce(initialState, action)
        assertThat(newState.isProcessingLink).isTrue()
    }

    @Test
    fun testSaveToContentBank_AppendsItemWithCorrectRankAndClearsLoading() {
        val stateWithLoading = initialState.copy(isProcessingLink = true)
        val action = GatekeeperAction.SaveToContentBank("vid1", "Test Title", ContentSource.YOUTUBE, ContentType.VIDEO, 1000L)
        val newState = reduce(stateWithLoading, action)
        assertThat(newState.contentItems).hasSize(1)
        assertThat(newState.contentItems.first().rank).isEqualTo(0L)
        assertThat(newState.isProcessingLink).isFalse()

        val action2 = GatekeeperAction.SaveToContentBank("vid2", "Test 2", ContentSource.YOUTUBE, ContentType.VIDEO, 2000L)
        val finalState = reduce(newState, action2)
        assertThat(finalState.contentItems).hasSize(2)
        assertThat(finalState.contentItems[1].rank).isEqualTo(1L)
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

        val state = initialState.copy(contentItems = listOf(item1, item2, item3))

        // Move item at index 0 to index 2 (Bottom of the list)
        val action = GatekeeperAction.ReorderContentBank(fromIndex = 0, toIndex = 2, currentTimestamp = 1000L)
        val newState = reduce(state, action)

        assertThat(newState.contentItems[0].id).isEqualTo("2")
        assertThat(newState.contentItems[0].rank).isEqualTo(0L)

        assertThat(newState.contentItems[1].id).isEqualTo("3")
        assertThat(newState.contentItems[1].rank).isEqualTo(1L)

        assertThat(newState.contentItems[2].id).isEqualTo("1")
        assertThat(newState.contentItems[2].rank).isEqualTo(2L)
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
        val state = initialState.copy(contentItems = listOf(item1))

        val newState = reduce(state, GatekeeperAction.RemoveFromContentBank("1", 1000L))
        assertThat(newState.contentItems.first().isDeleted).isTrue()
    }

    @Test
    fun testUpdateContentFilter_UpdatesState() {
        val action = GatekeeperAction.UpdateContentFilter(ContentType.AUDIO)
        val newState = reduce(initialState, action)
        assertThat(newState.activeContentFilter).isEqualTo(ContentType.AUDIO)
    }

    // --- Sync & Conflict Resolution Tests ---

    @Test
    fun testRemoteSyncCompleted_LastWriteWins_ResolvesConflicts() {
        // Arrange: Local state has an older VaultItem and a NEWER ContentItem
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
                vaultItems = listOf(localVaultItem),
                contentItems = listOf(localContentItem),
            )

        // Arrange: Incoming Remote state has a NEWER VaultItem but an OLDER ContentItem
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

        // Act
        val newState = reduce(stateWithLocal, action)

        // Assert: VaultItem - Remote won because its lastModified (2000) > Local (1000)
        assertThat(newState.vaultItems).hasSize(1)
        assertThat(newState.vaultItems.first().query).isEqualTo("Remote Query")

        // Assert: ContentItem - Local won because its lastModified (3000) > Remote (1500)
        assertThat(newState.contentItems).hasSize(1)
        assertThat(newState.contentItems.first().title).isEqualTo("Local Title")
    }

    @Test
    fun testUpdateSyncUrl_UpdatesState() {
        val action = GatekeeperAction.UpdateSyncUrl("http://127.0.0.1:8081")
        val newState = reduce(initialState, action)
        assertThat(newState.syncServerUrl).isEqualTo("http://127.0.0.1:8081")
    }

    @Test
    fun testLoginSuccess_SetsAuthState() {
        val action = GatekeeperAction.LoginSuccess("eyJ_MOCK_TOKEN")
        val newState = reduce(initialState, action)
        assertThat(newState.isAuthenticated).isTrue()
        assertThat(newState.jwtToken).isEqualTo("eyJ_MOCK_TOKEN")
    }

    @Test
    fun testLogout_ClearsAuthState() {
        val authState = initialState.copy(isAuthenticated = true, jwtToken = "eyJ_MOCK_TOKEN")
        val newState = reduce(authState, GatekeeperAction.Logout)
        assertThat(newState.isAuthenticated).isFalse()
        assertThat(newState.jwtToken).isNull()
    }

    // --- Surgical Web Engine Reducer Tests ---

    @Test
    fun testWebEngineInitialized_setsReadyState() {
        val action = GatekeeperAction.WebEngineInitialized
        val newState = reduce(initialState, action)
        assertThat(newState.isWebEngineReady).isTrue()
    }

    @Test
    fun testAddPinnedWebsite_AppendsToState() {
        val action = GatekeeperAction.AddPinnedWebsite("1", "My Site", "https://example.com")
        val newState = reduce(initialState, action)
        assertThat(newState.missionControlWebsites).hasSize(1)
        assertThat(newState.missionControlWebsites.first().label).isEqualTo("My Site")
    }

    @Test
    fun testRemovePinnedWebsite_RemovesFromState() {
        val site =
            com.aegisgatekeeper.app.domain
                .PinnedWebsite("1", "My Site", "https://example.com")
        val state = initialState.copy(missionControlWebsites = listOf(site))
        val action = GatekeeperAction.RemovePinnedWebsite("1")
        val newState = reduce(state, action)
        assertThat(newState.missionControlWebsites).isEmpty()
    }

    @Test
    fun testOpenPinnedWebsite_SetsUrl() {
        val action = GatekeeperAction.OpenPinnedWebsite("https://example.com")
        val newState = reduce(initialState, action)
        assertThat(newState.activePinnedWebsiteUrl).isEqualTo("https://example.com")
    }

    @Test
    fun testClosePinnedWebsite_ClearsUrl() {
        val state = initialState.copy(activePinnedWebsiteUrl = "https://example.com")
        val action = GatekeeperAction.ClosePinnedWebsite
        val newState = reduce(state, action)
        assertThat(newState.activePinnedWebsiteUrl).isNull()
    }

    @Test
    fun testSurgicalNavigationRequested_updatesUrl() {
        val url = "https://google.com"
        val action = GatekeeperAction.SurgicalNavigationRequested(url)
        val newState = reduce(initialState, action)
        assertThat(newState.currentSurgicalUrl).isEqualTo(url)
    }

    // --- YouTube Reducer Tests ---

    @Test
    fun testSearchYouTubeRequested_setsLoadingState() {
        // Arrange
        val action = GatekeeperAction.SearchYouTubeRequested("functional programming")

        // Act
        val newState = reduce(initialState, action)

        // Assert
        assertThat(newState.isLoadingYouTube).isTrue()
        assertThat(newState.youtubeSearchResults).isEmpty()
    }

    @Test
    fun testYouTubeSearchCompleted_populatesResults_clearsLoading() {
        // Arrange
        val stateBefore = initialState.copy(isLoadingYouTube = true)
        val mockResults =
            listOf(
                YoutubeSearchItem(
                    id = ItemId("videoId1"),
                    snippet = YoutubeSnippet("Title 1", "Channel 1", Thumbnails(ThumbnailInfo("url1"))),
                ),
            )
        val action = GatekeeperAction.YouTubeSearchCompleted(mockResults)

        // Act
        val newState = reduce(stateBefore, action)

        // Assert
        assertThat(newState.isLoadingYouTube).isFalse()
        assertThat(newState.youtubeSearchResults).hasSize(1)
        assertThat(
            newState.youtubeSearchResults
                .first()
                .id.videoId,
        ).isEqualTo("videoId1")
    }

    @Test
    fun testYouTubeSearchFailed_clearsLoadingState() {
        // Arrange
        val stateBefore = initialState.copy(isLoadingYouTube = true)

        // Act
        val newState = reduce(stateBefore, GatekeeperAction.YouTubeSearchFailed(com.aegisgatekeeper.app.api.YoutubeError.RateLimitExceeded))

        // Assert
        assertThat(newState.isLoadingYouTube).isFalse()
        assertThat(newState.youtubeSearchResults).isEmpty()
    }

    @Test
    fun testOpenSurgicalFacebook_updatesUrl() {
        val url = "https://m.facebook.com/groups/123"
        val action = GatekeeperAction.OpenSurgicalFacebook(url)
        val newState = reduce(initialState, action)
        assertThat(newState.activeFacebookUrl).isEqualTo(url)
    }

    @Test
    fun testCloseSurgicalFacebook_clearsUrl() {
        val activeState = initialState.copy(activeFacebookUrl = "https://m.facebook.com/groups/")
        val newState = reduce(activeState, GatekeeperAction.CloseSurgicalFacebook)
        assertThat(newState.activeFacebookUrl).isNull()
    }

    @Test
    fun testOpenCleanPlayer_setsActiveVideoId() {
        // Arrange
        val action = GatekeeperAction.OpenCleanPlayer("testVideoId")

        // Act
        val newState = reduce(initialState, action)

        // Assert
        assertThat(newState.activeVideoId).isEqualTo("testVideoId")
    }

    @Test
    fun testCloseCleanPlayer_clearsActiveVideoId() {
        // Arrange
        val stateBefore = initialState.copy(activeVideoId = "testVideoId")

        // Act
        val newState = reduce(stateBefore, GatekeeperAction.CloseCleanPlayer)

        // Assert
        assertThat(newState.activeVideoId).isNull()
    }

    @Test
    fun testSaveMediaPosition_UpdatesSavedPositions() {
        val action = GatekeeperAction.SaveMediaPosition("testVideoId", 120.5f)
        val newState = reduce(initialState, action)

        assertThat(newState.savedMediaPositions["testVideoId"]).isEqualTo(120.5f)
    }

    // --- Intentional Content Reducer Tests ---

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

        // Save to Slot 0
        val action1 = GatekeeperAction.SaveIntentionalSlot(slotIndex = 0, contentItem = mockContent1)
        val state1 = reduce(initialState, action1)
        assertThat(state1.intentionalSlots).hasSize(1)
        assertThat(
            state1.intentionalSlots
                .first()
                .contentItem.id,
        ).isEqualTo("c1")

        // Overwrite Slot 0
        val action2 = GatekeeperAction.SaveIntentionalSlot(slotIndex = 0, contentItem = mockContent2)
        val state2 = reduce(state1, action2)
        assertThat(state2.intentionalSlots).hasSize(1)
        assertThat(
            state2.intentionalSlots
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
        val state1 = reduce(initialState, GatekeeperAction.SaveIntentionalSlot(slotIndex = 2, contentItem = mockContent))

        val state2 = reduce(state1, GatekeeperAction.ClearIntentionalSlot(slotIndex = 2))
        assertThat(state2.intentionalSlots).isEmpty()
    }

    @Test
    fun testOpenCleanAudioPlayer_SetsActiveAudioUrl() {
        val newState = reduce(initialState, GatekeeperAction.OpenCleanAudioPlayer("https://soundcloud.com/test"))
        assertThat(newState.activeAudioUrl).isEqualTo("https://soundcloud.com/test")
    }

    @Test
    fun testCloseCleanAudioPlayer_ClearsActiveAudioUrl() {
        val activeState = initialState.copy(activeAudioUrl = "https://soundcloud.com/test")
        val newState = reduce(activeState, GatekeeperAction.CloseCleanAudioPlayer)
        assertThat(newState.activeAudioUrl).isNull()
    }

    // --- Unified Policy & Check-In Reducer Tests ---

    @Test
    fun testSetManualLockdown_UpdatesState() {
        val action = GatekeeperAction.SetManualLockdown(true)
        val newState = reduce(initialState, action)
        assertThat(newState.isManualLockdownActive).isTrue()
    }

    @Test
    fun testUpdateGroupCombinator_UpdatesState() {
        val action = GatekeeperAction.UpdateGroupCombinator("group1", RuleCombinator.ALL)
        val newState = reduce(initialState, action)
        assertThat(newState.appGroups.first().combinator).isEqualTo(RuleCombinator.ALL)
    }

    @Test
    fun testUpdateGroupApps_UpdatesState() {
        val action = GatekeeperAction.UpdateGroupApps("group1", setOf("com.new.app1", "com.new.app2"))
        val newState = reduce(initialState, action)
        assertThat(newState.appGroups.first().apps).containsExactly("com.new.app1", "com.new.app2")
    }

    @Test
    fun testAddDomainBlockRule_AppendsRule() {
        val action =
            GatekeeperAction.AddDomainBlockRule(
                id = "domainRule1",
                groupId = "group1",
                domains = setOf("reddit.com", "youtube.com"),
            )
        val newState = reduce(initialState, action)
        assertThat(newState.appGroups.first().rules).hasSize(1)

        val rule =
            newState.appGroups
                .first()
                .rules
                .first()
        assertThat(rule).isInstanceOf(BlockingRule.DomainBlock::class.java)
        assertThat((rule as BlockingRule.DomainBlock).domains).containsExactly("reddit.com", "youtube.com")
    }

    @Test
    fun testUpdateDomainBlockRule_UpdatesDomains() {
        val initialAction = GatekeeperAction.AddDomainBlockRule("domainRule1", "group1", setOf("reddit.com"))
        val stateWithRule = reduce(initialState, initialAction)

        val updateAction = GatekeeperAction.UpdateDomainBlockRule("domainRule1", "group1", setOf("reddit.com", "news.ycombinator.com"))
        val newState = reduce(stateWithRule, updateAction)

        val rule =
            newState.appGroups
                .first()
                .rules
                .first() as BlockingRule.DomainBlock
        assertThat(rule.domains).containsExactly("reddit.com", "news.ycombinator.com")
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
        val newState = reduce(initialState, action)
        assertThat(newState.appGroups.first().rules).hasSize(1)
        assertThat(
            newState.appGroups
                .first()
                .rules
                .first(),
        ).isInstanceOf(BlockingRule.CheckIn::class.java)
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
        val newState = reduce(initialState, action)
        assertThat(newState.consumedCheckIns).hasSize(1)
        assertThat(newState.consumedCheckIns.first().timeMinutes).isEqualTo(600)
        assertThat(newState.activeWhitelists).containsKey(blacklistedApp)
        assertThat(newState.activeWhitelists[blacklistedApp]!!.reason).isEqualTo("Needed an unblock")
        // The session duration was 15 mins (900,000 ms)
        assertThat(newState.activeWhitelists[blacklistedApp]!!.allocatedDurationMillis).isEqualTo(900_000L)
    }

    @Test
    fun testEndGroupSession_RemovesWhitelist() {
        val stateWithWhitelist =
            initialState.copy(
                activeWhitelists = mapOf(blacklistedApp to TemporaryWhitelist(blacklistedApp, "Test", 0L, 1000L, 1000L)),
            )
        val action = GatekeeperAction.EndGroupSession("group1")
        val newState = reduce(stateWithWhitelist, action)
        assertThat(newState.activeWhitelists).isEmpty()
    }

    // --- Subscription Reducer Tests ---

    @Test
    fun testClearExportData_ClearsState() {
        val stateWithData = initialState.copy(exportData = "Some data")
        val newState = reduce(stateWithData, GatekeeperAction.ClearExportData)
        assertThat(newState.exportData).isNull()
    }

    @Test
    fun testFrictionCompleted_IncrementsBypassCounter() {
        val action = GatekeeperAction.FrictionCompleted("com.test", 900_000L, 1000L)
        val newState = reduce(initialState, action)
        assertThat(newState.analyticsBypasses).isEqualTo(1)
    }

    @Test
    fun testGenerateExportData_UpdatesState() {
        val newState = reduce(initialState, GatekeeperAction.UpgradeToProTier)
        assertThat(newState.isProTier).isTrue()
    }

    @Test
    fun testSetFrictionGame_UpdatesState() {
        val action = GatekeeperAction.SetFrictionGame(com.aegisgatekeeper.app.domain.FrictionGame.HOLD_STEADY)
        val newState = reduce(initialState, action)
        assertThat(newState.activeFrictionGame).isEqualTo(com.aegisgatekeeper.app.domain.FrictionGame.HOLD_STEADY)
    }

    @Test
    fun testUpdateMissionControlApps_UpdatesState() {
        val apps = listOf("com.example.app1", "com.example.app2")
        val action = GatekeeperAction.UpdateMissionControlApps(apps)
        val newState = reduce(initialState, action)
        assertThat(newState.missionControlApps).isEqualTo(apps)
    }

    // --- Metacognition Reducer Tests ---

    @Test
    fun testLogSessionMetacognition_AppendsToSessionLogs() {
        val action =
            GatekeeperAction.LogSessionMetacognition(
                packageName = "CleanPlayer: YouTube",
                durationMillis = 120000L,
                emotion = com.aegisgatekeeper.app.domain.Emotion.HAPPY,
                currentTimestamp = 12345L,
            )

        val newState = reduce(initialState, action)

        assertThat(newState.sessionLogs).hasSize(1)
        val log = newState.sessionLogs.first()
        assertThat(log.packageName).isEqualTo("CleanPlayer: YouTube")
        assertThat(log.durationMillis).isEqualTo(120000L)
        assertThat(log.emotion).isEqualTo(com.aegisgatekeeper.app.domain.Emotion.HAPPY)
    }

    // --- Permissions & Onboarding Reducer Tests ---

    @Test
    fun testPermissionsUpdated_setsFlagsAndCalculatesDualMoatStatus() {
        // Arrange
        val action =
            GatekeeperAction.PermissionsUpdated(
                hasOverlay = true,
                hasUsageAccess = true,
                hasAccessibility = false,
                isBatteryDisabled = false,
            )

        // Act 1: Partial permissions
        val partialState = reduce(initialState, action)

        // Assert 1
        assertThat(partialState.hasOverlayPermission).isTrue()
        assertThat(partialState.hasUsageAccessPermission).isTrue()
        assertThat(partialState.hasAccessibilityPermission).isFalse()
        assertThat(partialState.isBatteryOptimizationDisabled).isFalse()
        assertThat(partialState.isDualMoatEnabled).isFalse() // Must be false until all are granted

        // Act 2: All permissions granted
        val fullAction = action.copy(hasAccessibility = true, isBatteryDisabled = true)
        val finalState = reduce(initialState, fullAction)

        // Assert 2
        assertThat(finalState.isDualMoatEnabled).isTrue()
    }
}
