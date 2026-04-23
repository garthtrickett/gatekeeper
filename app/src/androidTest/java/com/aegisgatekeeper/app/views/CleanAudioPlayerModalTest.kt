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
class CleanAudioPlayerModalTest {
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
            val isVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            GatekeeperTheme {
                if (isVisible.value) {
                    CleanAudioPlayerModal(url = "https://soundcloud.com/test", onClose = {
                        closed = true
                        isVisible.value = false
                    })
                }
            }
        }

        composeTestRule.waitForIdle()

        // Assert the close button exists
        composeTestRule.onNodeWithText("Close Audio").assertExists()

        // Perform click to stop session
        composeTestRule.onNodeWithText("End Session").performClick()
        composeTestRule.waitForIdle()

        // Verify callback was triggered
        assertThat(closed).isTrue()

        // Verify state manager got the TriggerMetacognition action
        val state = GatekeeperStateManager.state.value
        assertThat(state.pendingMetacognition).isNotNull()
        assertThat(state.pendingMetacognition!!.packageName).isEqualTo("CleanAudio: Player")
    }
}
