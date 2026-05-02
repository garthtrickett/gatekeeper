package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.domain.NotificationLog
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDigestUiTest {
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
    fun testSniperReply_FuzzyMatchAndScheduleMessage() {
        // Arrange: Seed state with an intercepted notification and a matching Beeper chat
        val mockChat = BeeperChat("room123", "John Doe", "WhatsApp")
        GatekeeperStateManager.dispatch(GatekeeperAction.BeeperChatsLoaded(listOf(mockChat)))

        val mockLog =
            NotificationLog(
                id = "log1",
                packageName = "com.whatsapp",
                title = "WhatsApp: John Doe",
                content = "Hey, are we still on for tonight?",
                timestamp = System.currentTimeMillis(),
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.LoadNotificationDigest(listOf(mockLog)))

        // Force the Vault to be unlocked so the Digest is visible (Gathering Phase covers the whole day)
        GatekeeperStateManager.dispatch(GatekeeperAction.UpdatePhaseWindows(0, 0, 0, 1440))

        composeTestRule.setContent {
            GatekeeperTheme {
                NotificationDigestScreen()
            }
        }

        // Assert: Notification is displayed
        composeTestRule.onNodeWithText("WhatsApp: John Doe").assertIsDisplayed()

        // Assert & Act: "Outpost Reply" button should be visible because of the fuzzy match
        composeTestRule.onNodeWithText("Outpost Reply").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outpost Reply").performClick()
        composeTestRule.waitForIdle()

        // Act: Type a reply and send
        composeTestRule.onNodeWithText("Reply to John Doe").performTextInput("Yes, see you at 8!")
        composeTestRule.onNodeWithText("Send Now").performClick()
        composeTestRule.waitForIdle()

        // Assert: UI updates to Replied
        composeTestRule.onNodeWithText("Replied ✓").assertIsDisplayed()

        // Assert: Message was scheduled in the StateManager
        val state = GatekeeperStateManager.state.value
        assertThat(state.scheduledMessages).hasSize(1)
        assertThat(state.scheduledMessages.first().messageText).isEqualTo("Yes, see you at 8!")
        assertThat(state.scheduledMessages.first().beeperRoomId).isEqualTo("room123")
    }
}
