package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
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
    fun testSettingsScreen_UpdatesPhaseWindows() {
        composeTestRule.setContent {
            GatekeeperTheme {
                SettingsScreen()
            }
        }

        // Verify initial UI elements
        composeTestRule.onNodeWithText("Phase Boundaries").assertIsDisplayed()

        // Update Deep Work Start Time (Assume starting is 09:00 based on default 540)
        composeTestRule.onNodeWithText("09:00").performTextReplacement("08:00") // Should be 480 mins

        // Update Gathering End Time (Assume ending is 18:30 based on default 1110)
        composeTestRule.onNodeWithText("18:30").performTextReplacement("19:00") // Should be 1140 mins

        // Click Save
        composeTestRule.onNodeWithText("Save Phase Times").performClick()
        composeTestRule.waitForIdle()

                // Verify state is updated
        val state = GatekeeperStateManager.state.value
        assertThat(state.deepWorkStartMinutes).isEqualTo(480)
        assertThat(state.gatheringEndMinutes).isEqualTo(1140)
    }

    @Test
    fun testSettingsScreen_BeeperConnectionState() {
        // Arrange: Seed the state with a dummy Beeper chat
        val mockChat = com.aegisgatekeeper.app.domain.BeeperChat("1", "Test User", "WhatsApp")
        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.BeeperChatsLoaded(listOf(mockChat))
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                SettingsScreen()
            }
        }

        // Assert: The button should reflect the connected status
        composeTestRule.onNodeWithText("Beeper Connected ✓ (Resync)").assertIsDisplayed()
    }
}
