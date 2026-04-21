package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.api.ItemId
import com.aegisgatekeeper.app.api.ThumbnailInfo
import com.aegisgatekeeper.app.api.Thumbnails
import com.aegisgatekeeper.app.api.YoutubeSearchItem
import com.aegisgatekeeper.app.api.YoutubeSnippet
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleanYouTubeScreenTest {
    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @After
    fun tearDown() {
        // Reset the singleton state to prevent test leakage
        GatekeeperStateManager.resetStateForTest()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testInitialRendering() {
        composeTestRule.setContent {
            GatekeeperTheme {
                CleanYouTubeScreen()
            }
        }

        composeTestRule.onNodeWithText("Surgical Search").assertExists()
        composeTestRule.onNodeWithText("Search for intentional content...").assertExists()
    }

    @Test
    fun testSearchResultsRenderedCorrectly() {
        // Arrange: Seed the state manager with mock YouTube results
        val mockResults =
            listOf(
                YoutubeSearchItem(
                    id = ItemId("mock_video_id"),
                    snippet =
                        YoutubeSnippet(
                            title = "Deep Work Soundtrack",
                            channelTitle = "Focus Channel",
                            thumbnails = Thumbnails(high = ThumbnailInfo("https://example.com/thumb.jpg")),
                        ),
                ),
            )

        // Dispatching the completed action skips the loading phase and immediately updates the state.
        GatekeeperStateManager.dispatch(
            GatekeeperAction.YouTubeSearchCompleted(mockResults),
        )

        // Act
        composeTestRule.setContent {
            GatekeeperTheme {
                CleanYouTubeScreen()
            }
        }

        // Assert: Verify the mocked details are visible
        composeTestRule.onNodeWithText("Deep Work Soundtrack").assertExists()
        composeTestRule.onNodeWithText("Focus Channel").assertExists()
    }
}
