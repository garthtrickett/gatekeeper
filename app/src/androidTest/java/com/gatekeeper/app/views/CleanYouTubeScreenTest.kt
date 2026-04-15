package com.gatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeeper.app.GatekeeperStateManager
import com.gatekeeper.app.MainActivity
import com.gatekeeper.app.api.ItemId
import com.gatekeeper.app.api.ThumbnailInfo
import com.gatekeeper.app.api.Thumbnails
import com.gatekeeper.app.api.YoutubeSearchItem
import com.gatekeeper.app.api.YoutubeSnippet
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.resetStateForTest
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
            androidx.compose.material3.MaterialTheme {
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
            androidx.compose.material3.MaterialTheme {
                CleanYouTubeScreen()
            }
        }

        // Assert: Verify the mocked details are visible
        composeTestRule.onNodeWithText("Deep Work Soundtrack").assertExists()
        composeTestRule.onNodeWithText("Focus Channel").assertExists()
    }
}
