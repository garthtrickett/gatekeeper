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

        composeTestRule.setContent {
            GatekeeperTheme {
                FeedManagementDialog(onDismiss = {})
            }
        }

        // Assert: The subscription is visible
        composeTestRule.onNodeWithText("Manage Podcasts").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Sovereign Podcast").assertIsDisplayed()

        // Act: Click the subscription row to drill down
        composeTestRule.onNodeWithText("The Sovereign Podcast").performClick()
        composeTestRule.waitForIdle()

        // Assert: State updated to active podcast
        val state = GatekeeperStateManager.state.value
        assertThat(state.activePodcastId).isEqualTo("podcast_123")
        assertThat(state.isLoadingEpisodes).isTrue() // Because we dispatched LoadPodcastEpisodes
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
                RssEpisode(
                    title = "Episode 1: Focus",
                    audioUrl = "https://example.com/ep1.mp3",
                    durationSeconds = 3600L,
                    pubDate = "Jan 01",
                ),
            )

        // Force the drill-down state manually without triggering network side-effects
        GatekeeperStateManager.dispatch(GatekeeperAction.PodcastEpisodesLoaded(mockEpisodes, sub.id))

        composeTestRule.setContent {
            GatekeeperTheme {
                FeedManagementDialog(onDismiss = {})
            }
        }

        // Assert: The episode list view is visible
        composeTestRule.onNodeWithText("The Sovereign Podcast").assertIsDisplayed() // Title header
        composeTestRule.onNodeWithText("Episode 1: Focus").assertIsDisplayed()
        composeTestRule.onNodeWithText("+").assertIsDisplayed() // Add button

        // Act: Click the '+' button to add to bank
        composeTestRule.onNodeWithText("+").performClick()
        composeTestRule.waitForIdle()

        // Assert: Check that it was added to the bank (button should now be a checkmark)
        // The AddEpisodeToBank action dispatches SaveToContentBank immediately.
        val state = GatekeeperStateManager.state.value
        val bankedItem = state.contentItems.find { it.videoId == "https://example.com/ep1.mp3" }
        assertThat(bankedItem).isNotNull()
        assertThat(bankedItem?.title).isEqualTo("Episode 1: Focus")

        // The button should now say "Download" because it's added to the content bank
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download").assertIsEnabled()
    }

    @Test
    fun testFeedManagement_BackButton_ClearsActivePodcast() {
        // Arrange: Start deep in the episodes view
        val sub = PodcastSubscription(id = "podcast_123", feedUrl = "https://example.com/feed.xml", showTitle = "Title", artworkUrl = null)
        GatekeeperStateManager.dispatch(GatekeeperAction.SavePodcastSubscription(sub))
        // We directly dispatch PodcastEpisodesLoaded to set the state and avoid triggering an actual network request.
        GatekeeperStateManager.dispatch(GatekeeperAction.PodcastEpisodesLoaded(emptyList(), sub.id))

        composeTestRule.setContent {
            GatekeeperTheme {
                FeedManagementDialog(onDismiss = {})
            }
        }

        // Assert: We are in the drill-down view
        composeTestRule.onNodeWithText("Title").assertIsDisplayed()

        // Act: Click "Back"
        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()

        // Assert: We are back at the subscriptions list
        assertThat(GatekeeperStateManager.state.value.activePodcastId).isNull()
        composeTestRule.onNodeWithText("Manage Podcasts").assertIsDisplayed()
    }
}
