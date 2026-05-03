package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.api.RssEpisode
import com.aegisgatekeeper.app.domain.CachedEpisode
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.domain.PodcastSubscription
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedManagementUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testFeedManagement_ShowsSubscriptionsAndDrillsDown() {
        // Arrange: Seed a subscription
        val sub =
            PodcastSubscription(
                id = "podcast_123",
                feedUrl = "https://example.com/feed.xml",
                showTitle = "The Sovereign Podcast",
                artworkUrl = null,
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.SavePodcastSubscription(sub))

        val showDialog = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            GatekeeperTheme {
                if (showDialog.value) FeedManagementDialog(onDismiss = { showDialog.value = false })
            }
        }

        // Assert: The subscription is visible
        composeTestRule.onNodeWithText("Manage Podcasts").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Sovereign Podcast").assertIsDisplayed()

        // Act: Click the subscription row to drill down
        composeTestRule.onNodeWithText("The Sovereign Podcast").performClick()
        composeTestRule.waitForIdle()

        // Assert: State updated to active podcast (don't sleep, avoid network failure race)
        val state = GatekeeperStateManager.state.value
        assertThat(state.media.activePodcastId).isEqualTo("podcast_123")
        assertThat(state.media.isLoadingEpisodes).isTrue() // Because we dispatched LoadPodcastEpisodes

        showDialog.value = false
        composeTestRule.waitForIdle()
    }

    @Test
    fun testFeedManagement_RendersEpisodesAndAddsToBank() {
        // Arrange: Seed a subscription and mock the episodes loaded state
        val sub =
            PodcastSubscription(
                id = "podcast_123",
                feedUrl = "https://example.com/feed.xml",
                showTitle = "The Sovereign Podcast",
                artworkUrl = null,
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.SavePodcastSubscription(sub))

        val mockEpisodes =
            listOf(
                CachedEpisode(
                    podcastId = "podcast_123",
                    title = "Episode 1: Focus",
                    audioUrl = "https://example.com/ep1.mp3",
                    durationSeconds = 3600L,
                    pubDate = "Jan 01",
                ),
            )

        // Force the drill-down state manually without triggering network side-effects
        GatekeeperStateManager.dispatch(GatekeeperAction.PodcastEpisodesLoaded(mockEpisodes, sub.id))

        val showDialog = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            GatekeeperTheme {
                if (showDialog.value) FeedManagementDialog(onDismiss = { showDialog.value = false })
            }
        }

        // Assert: The episode list view is visible
        composeTestRule.onNodeWithText("The Sovereign Podcast").assertIsDisplayed() // Title header
        composeTestRule.onNodeWithText("Episode 1: Focus").assertIsDisplayed()
        composeTestRule.onNodeWithText("+").assertIsDisplayed() // Add button

        // Act: Click the '+' button to add to bank
        composeTestRule.onNodeWithText("+").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500) // Wait for Coroutine side-effect dispatch

        // Assert: Check that it was added to the bank (button should now be a checkmark)
        // The AddEpisodeToBank action dispatches SaveToContentBank immediately.
        val state = GatekeeperStateManager.state.value
        val bankedItem = state.data.contentItems.find { it.videoId == "https://example.com/ep1.mp3" }
        assertThat(bankedItem).isNotNull()
        assertThat(bankedItem?.title).isEqualTo("Episode 1: Focus")

        // The button should now say "Download" because it's added to the content bank
        composeTestRule.onNodeWithText("Download").assertExists()

        showDialog.value = false
        composeTestRule.waitForIdle()
    }

    @Test
    fun testFeedManagement_LatestEpisodesFeed_RendersAndAddsToBank() {
        // Arrange: Seed a subscription and global episodes state
        val sub =
            PodcastSubscription(
                id = "podcast_123",
                feedUrl = "https://example.com/feed.xml",
                showTitle = "The Sovereign Podcast",
                artworkUrl = null,
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.SavePodcastSubscription(sub))

        val mockUnified =
            listOf(
                com.aegisgatekeeper.app.domain.UnifiedEpisode(
                    id = "ep_1",
                    podcastId = "podcast_123",
                    title = "Global Episode 1",
                    audioUrl = "https://example.com/global1.mp3",
                    durationSeconds = 1800L,
                    pubDate = "Feb 01",
                    lastModified = 0L,
                    showTitle = "The Sovereign Podcast",
                    artworkUrl = null,
                ),
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.LatestGlobalEpisodesLoaded(mockUnified))

        val showDialog = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            GatekeeperTheme {
                if (showDialog.value) FeedManagementDialog(onDismiss = { showDialog.value = false })
            }
        }

        // Act: Switch to Latest Episodes tab
        composeTestRule.onNodeWithText("Latest Episodes").performClick()
        composeTestRule.waitForIdle()

        // Assert: Verify unified content renders correctly with the parent show title
        composeTestRule.onNodeWithText("Global Episode 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Sovereign Podcast • Feb 01 • 30m").assertIsDisplayed()

        // Act: Click the '+' button to add to bank
        composeTestRule.onNodeWithText("+").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500) // Wait for Coroutine side-effect dispatch

        // Assert: Check that it was added to the bank (button should now be 'Download')
        val state = GatekeeperStateManager.state.value
        val bankedItem = state.data.contentItems.find { it.videoId == "https://example.com/global1.mp3" }
        assertThat(bankedItem).isNotNull()
        assertThat(bankedItem?.title).isEqualTo("Global Episode 1")
        assertThat(bankedItem?.channelName).isEqualTo("The Sovereign Podcast")

        composeTestRule.onNodeWithText("Download").assertExists()

        // Act: Click Refresh and verify state update
        composeTestRule.onNodeWithText("Refresh").performClick()
        composeTestRule.waitForIdle()
        assertThat(GatekeeperStateManager.state.value.sync.isSyncingPodcasts).isTrue()

        showDialog.value = false
        composeTestRule.waitForIdle()
    }

    @Test
    fun testFeedManagement_BackButton_ClearsActivePodcast() {
        // Arrange: Start deep in the episodes view
        val sub = PodcastSubscription(id = "podcast_123", feedUrl = "https://example.com/feed.xml", showTitle = "Title", artworkUrl = null)
        GatekeeperStateManager.dispatch(GatekeeperAction.SavePodcastSubscription(sub))
        // We directly dispatch PodcastEpisodesLoaded to set the state and avoid triggering an actual network request.
        GatekeeperStateManager.dispatch(GatekeeperAction.PodcastEpisodesLoaded(emptyList(), sub.id))

        val showDialog = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            GatekeeperTheme {
                if (showDialog.value) FeedManagementDialog(onDismiss = { showDialog.value = false })
            }
        }

        // Assert: We are in the drill-down view
        composeTestRule.onNodeWithText("Title").assertIsDisplayed()

        // Act: Click "Back"
        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()

        // Assert: We are back at the subscriptions list
        assertThat(GatekeeperStateManager.state.value.media.activePodcastId).isNull()
        composeTestRule.onNodeWithText("Manage Podcasts").assertIsDisplayed()

        showDialog.value = false
        composeTestRule.waitForIdle()
    }
}
