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
            val isVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            GatekeeperTheme {
                CleanPlayerModal(
                    videoId = "test1234",
                    isVisible = isVisible.value,
                    onMinimize = { isVisible.value = false },
                    onStop = {
                        closed = true
                        isVisible.value = false
                    }
                )
            }
        }

        // Assert the stop button exists
        composeTestRule.onNodeWithText("End Session").assertExists()

        // Perform click to stop session
        composeTestRule.onNodeWithText("End Session").performClick()
        composeTestRule.waitForIdle()

        // Verify callback was triggered
        assertThat(closed).isTrue()

        // Verify state manager got the TriggerMetacognition action
        val state = GatekeeperStateManager.state.value
        assertThat(state.pendingMetacognition).isNotNull()
        assertThat(state.pendingMetacognition!!.packageName).isEqualTo("CleanPlayer: YouTube")
    }

    @Test
    fun testModalMinimizes_DoesNotTriggerMetacognition() {
        var minimized = false

        composeTestRule.setContent {
            val isVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            GatekeeperTheme {
                CleanPlayerModal(
                    videoId = "test1234",
                    isVisible = isVisible.value,
                    onMinimize = { 
                        minimized = true
                        isVisible.value = false 
                    },
                    onStop = { }
                )
            }
        }

        // Assert the minimize button exists
        composeTestRule.onNodeWithText("Minimize").assertExists()

        // Perform click to minimize session
        composeTestRule.onNodeWithText("Minimize").performClick()
        composeTestRule.waitForIdle()

        // Verify callback was triggered
        assertThat(minimized).isTrue()

        // Verify state manager did NOT get the TriggerMetacognition action
        val state = GatekeeperStateManager.state.value
        assertThat(state.pendingMetacognition).isNull()
    }
}
