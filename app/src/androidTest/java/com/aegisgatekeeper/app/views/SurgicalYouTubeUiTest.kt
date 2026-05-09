package com.aegisgatekeeper.app.views

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurgicalYouTubeUiTest {
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
    fun testYouTubeOverlay_AppearsAndCloses() {
        // Arrange: Open the YouTube Surgical Screen
        val testUrl = "gatekeeper://safe_channels"
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalYouTube(testUrl))

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

        // Assert: Screen components exist (Navigation buttons)
        composeTestRule.onNodeWithText("Safe Channels").assertExists()
        composeTestRule.onNodeWithText("Search").assertExists()
        composeTestRule.onNodeWithText("Exit").assertExists()

        // Act: Click Exit
        composeTestRule.onNodeWithText("Exit").performClick()
        composeTestRule.waitForIdle()

        // Assert: URL is cleared in state manager
        assertThat(GatekeeperStateManager.state.value.media.activeYouTubeUrl).isNull()
        composeTestRule.onNodeWithText("Exit").assertDoesNotExist()
    }

        @Test
    fun testYouTubeScreen_TabSwitching_UpdatesState() {
        // Start at Safe Channels
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalYouTube("gatekeeper://safe_channels"))

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

        composeTestRule.onNodeWithText("Safe Channels").assertExists()
        composeTestRule.onNodeWithText("Search").assertExists()

        // Act: Click Search
        composeTestRule.onNodeWithText("Search").performClick()
        composeTestRule.waitForIdle()

        // Assert: State updated to Search URL with our new default query
        composeTestRule.waitUntil(5000) {
            GatekeeperStateManager.state.value.media.activeYouTubeUrl
                ?.contains("search_query=") == true
        }
        val state = GatekeeperStateManager.state.value
        assertThat(state.media.activeYouTubeUrl).contains("search_query=")
    }
}
