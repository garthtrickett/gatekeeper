package com.aegisgatekeeper.app.views

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurgicalYouTubeScrollTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testYouTubeSearch_RemainsResponsiveToSpamScrolling() {
        // Arrange: Open Surgical YouTube to a search results page
        GatekeeperStateManager.dispatch(
            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/results?search_query=test"),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                if (state.media.activeYouTubeUrl != null) {
                    SurgicalYouTubeScreen(
                        url = state.media.activeYouTubeUrl!!,
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalYouTube) },
                    )
                }
            }
        }

        // Wait for WebView to render
        composeTestRule.waitForIdle()

        // Act: Perform multiple rapid swipes (spam scroll)
        composeTestRule.onNodeWithText("Subscriptions").assertExists()

        repeat(5) {
            composeTestRule.onRoot().performTouchInput {
                swipeUp(durationMillis = 100)
            }
        }

        // Assert: Ensure the 'Exit' button is still interactive and hasn't been blocked by a hung UI thread
        composeTestRule.onNodeWithText("Exit").performClick()
        assert(GatekeeperStateManager.state.value.media.activeYouTubeUrl == null)
    }
}
