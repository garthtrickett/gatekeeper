package com.gatekeeper.app.domain

import com.gatekeeper.app.api.ItemId
import com.gatekeeper.app.api.ThumbnailInfo
import com.gatekeeper.app.api.Thumbnails
import com.gatekeeper.app.api.YoutubeSearchItem
import com.gatekeeper.app.api.YoutubeSnippet
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
            blacklistedApps = setOf(blacklistedApp),
        )

    @Test
    fun testAppBroughtToForeground_Blacklisted_NoWhitelist_TriggersOverlay() {
        // Arrange: The app is blacklisted and has no active whitelist.
        val action = GatekeeperAction.AppBroughtToForeground(blacklistedApp, 1000L)

        // Act: We run the reducer.
        val newState = reduce(initialState, action)

        // Assert: The overlay should now be active for the intercepted app.
        assertThat(newState.isOverlayActive).isTrue()
        assertThat(newState.currentlyInterceptedApp).isEqualTo(blacklistedApp)
    }

    @Test
    fun testAppBroughtToForeground_NotBlacklisted_DoesNothing() {
        // Arrange: The app is not on our blacklist.
        val action = GatekeeperAction.AppBroughtToForeground(whitelistedApp, 1000L)

        // Act
        val newState = reduce(initialState, action)

        // Assert: The state should be completely unchanged.
        assertThat(newState).isEqualTo(initialState)
    }

    @Test
    fun testAppBroughtToForeground_Blacklisted_Whitelisted_DoesNothing() {
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
                            ),
                    ),
            )
        val action = GatekeeperAction.AppBroughtToForeground(blacklistedApp, 1000L)

        // Act
        val newState = reduce(stateWithWhitelist, action)

        // Assert: The state is unchanged; the user is allowed through.
        assertThat(newState).isEqualTo(stateWithWhitelist)
    }

    @Test
    fun testAppBroughtToForeground_Blacklisted_ExpiredWhitelist_TriggersOverlay() {
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
                            ),
                    ),
            )
        val action = GatekeeperAction.AppBroughtToForeground(blacklistedApp, 1000L)

        // Act
        val newState = reduce(stateWithExpiredWhitelist, action)

        // Assert: The overlay is triggered because the whitelist is no longer valid.
        assertThat(newState.isOverlayActive).isTrue()
        assertThat(newState.currentlyInterceptedApp).isEqualTo(blacklistedApp)
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
        val action = GatekeeperAction.MarkVaultItemResolved("123")

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

    // --- Content Bank Reducer Tests ---

    @Test
    fun testSaveToContentBank_AppendsItemWithCorrectRank() {
        val action = GatekeeperAction.SaveToContentBank("vid1", "Test Title", ContentSource.YOUTUBE, ContentType.VIDEO, 1000L)
        val newState = reduce(initialState, action)
        assertThat(newState.contentItems).hasSize(1)
        assertThat(newState.contentItems.first().rank).isEqualTo(0L)

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
        val action = GatekeeperAction.ReorderContentBank(fromIndex = 0, toIndex = 2)
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

        val newState = reduce(state, GatekeeperAction.RemoveFromContentBank("1"))
        assertThat(newState.contentItems).isEmpty()
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
        val newState = reduce(stateBefore, GatekeeperAction.YouTubeSearchFailed)

        // Assert
        assertThat(newState.isLoadingYouTube).isFalse()
        assertThat(newState.youtubeSearchResults).isEmpty()
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
}
