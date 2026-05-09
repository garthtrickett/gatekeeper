package com.aegisgatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Specialized integration tests verifying that the "Single Source of Truth" 
 * logic in the Reducer correctly enforces the surgical jail boundaries.
 */
@RunWith(AndroidJUnit4::class)
class SurgicalJailIntegrationTest {

    private val stateManager = GatekeeperStateManager

    @Before
    fun setup() {
        stateManager.resetStateForTest()
    }

    @After
    fun tearDown() {
        stateManager.resetStateForTest()
    }

    @Test
    fun youtube_WatchPage_IsHardBlockedByReducer() {
        // Arrange: Start in the Subscriptions jail
        val initialUrl = "https://m.youtube.com/feed/channels"
        stateManager.dispatch(GatekeeperAction.OpenSurgicalYouTube(initialUrl))
        assertThat(stateManager.state.value.media.activeYouTubeUrl).isEqualTo(initialUrl)

        // Act: Simulate user intent to navigate to a video
        val illegalUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(illegalUrl))

        // Assert: The Reducer should have rejected the change based on the URL pattern
        assertThat(stateManager.state.value.media.activeYouTubeUrl).isEqualTo(initialUrl)
    }

    @Test
    fun youtube_Shorts_IsHardBlockedByReducer() {
        val initialUrl = "https://m.youtube.com/feed/channels"
        stateManager.dispatch(GatekeeperAction.OpenSurgicalYouTube(initialUrl))

        // Act: Simulate intent to go to Shorts
        val illegalUrl = "https://m.youtube.com/shorts/12345"
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(illegalUrl))

        // Assert: State remains at the safe URL
        assertThat(stateManager.state.value.media.activeYouTubeUrl).isEqualTo(initialUrl)
    }

    @Test
    fun youtube_HomeFeed_IsAutomaticallyRedirected() {
        stateManager.dispatch(GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/feed/channels"))

        // Act: Attempt to go to the YouTube main landing page (the addictive feed)
        val homeUrl = "https://m.youtube.com/"
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(homeUrl))

        // Assert: The Reducer should have proactively rewritten the URL to the safe Subscriptions page
        assertThat(stateManager.state.value.media.activeYouTubeUrl).isEqualTo("https://m.youtube.com/feed/channels")
    }

    @Test
    fun facebook_NewsFeed_IsAutomaticallyRedirected() {
        // Arrange: Open Facebook in Groups mode
        val groupsUrl = "https://m.facebook.com/groups/?_rdr"
        stateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook(groupsUrl))

        // Act: Attempt to navigate to the Home feed
        val feedUrl = "https://m.facebook.com/home.php"
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(feedUrl))

        // Assert: Reducer forces state back to Groups
        assertThat(stateManager.state.value.media.activeFacebookUrl).isEqualTo("https://m.facebook.com/groups/?_rdr")
    }

    @Test
    fun genericWeb_Navigation_IsAllowedNormally() {
        // Arrange: Start with a generic DuckDuckGo search
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested("https://duckduckgo.com"))

        // Act: Navigate to Wikipedia
        val nextUrl = "https://en.wikipedia.org/wiki/Sovereignty"
        stateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(nextUrl))

        // Assert: Navigation is allowed because no jail rules apply to this domain
        assertThat(stateManager.state.value.media.currentSurgicalUrl).isEqualTo(nextUrl)
    }
}
