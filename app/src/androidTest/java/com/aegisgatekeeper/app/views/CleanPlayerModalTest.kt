package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleanPlayerModalTest {
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
    fun testModalRendersAndClosesWithSkip() {
        var closed = false

        composeTestRule.setContent {
            GatekeeperTheme {
                CleanPlayerModal(videoId = "test1234", onClose = { closed = true })
            }
        }

        // Assert the close button exists
        composeTestRule.onNodeWithText("Close Video").assertExists()

        // Perform click to show metacognition UI
        composeTestRule.onNodeWithText("Close Video").performClick()

        // Wait for UI to change and assert elements
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Was this worth it?").assertExists()

        // Skip to close
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        // Verify callback was triggered
        assertThat(closed).isTrue()

        // Verify state manager got the SKIPPED action
        val state = GatekeeperStateManager.state.value
        assertThat(state.sessionLogs).hasSize(1)
        assertThat(state.sessionLogs.first().emotion).isEqualTo(Emotion.SKIPPED)
    }

    @Test
    fun testModal_ClicksEmotion_LogsAndCloses() {
        var closed = false

        composeTestRule.setContent {
            val isVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            GatekeeperTheme {
                if (isVisible.value) {
                    CleanPlayerModal(videoId = "test1234", onClose = {
                        closed = true
                        isVisible.value = false
                    })
                }
            }
        }

        // Click Close Video
        composeTestRule.onNodeWithText("Close Video").performClick()
        composeTestRule.waitForIdle()

        // Click Happy
        composeTestRule.onNodeWithText("Happy").performClick()
        composeTestRule.waitForIdle()

        // Verify callback
        assertThat(closed).isTrue()

        // Verify state manager got the action
        val state = GatekeeperStateManager.state.value
        assertThat(state.sessionLogs).hasSize(1)
        assertThat(state.sessionLogs.first().emotion).isEqualTo(Emotion.HAPPY)
        assertThat(state.sessionLogs.first().packageName).isEqualTo("CleanPlayer: YouTube")

        // Verify media position was saved
        assertThat(state.savedMediaPositions["test1234"]).isEqualTo(0f)
    }
}
