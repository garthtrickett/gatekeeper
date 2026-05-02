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
import com.aegisgatekeeper.app.domain.ScheduledMessage
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutpostUiTest {
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
    fun testOutpostScreen_EmptyState() {
        composeTestRule.setContent {
            GatekeeperTheme {
                OutpostScreen()
            }
        }

        composeTestRule.onNodeWithText("Messaging Outpost").assertIsDisplayed()
        composeTestRule.onNodeWithText("No chats available. Go to Account settings to connect Beeper, then tap Sync.").assertIsDisplayed()
    }

        @Test
    fun testOutpostScreen_Composer_SchedulesMessage() {
        // Arrange: Inject a dummy Beeper chat into state
        val mockChat = BeeperChat("room123", "John Doe", "WhatsApp")
        GatekeeperStateManager.dispatch(GatekeeperAction.BeeperChatsLoaded(listOf(mockChat)))

        composeTestRule.setContent {
            GatekeeperTheme {
                OutpostScreen()
            }
        }

        // Act: Open dropdown and select chat
        composeTestRule.onNodeWithText("Select Chat").performClick()
        composeTestRule.onNodeWithText("John Doe (WhatsApp)").performClick()

        // Act: Type message
        composeTestRule.onNodeWithText("Message Body").performTextInput("See you at 6!")

        // Act: Click delay button
        composeTestRule.onNodeWithText("+1h").performClick()
        composeTestRule.waitForIdle()

        // Assert: Message was added to state
        val state = GatekeeperStateManager.state.value
        assertThat(state.scheduledMessages).hasSize(1)
        val msg = state.scheduledMessages.first()
        assertThat(msg.chatName).isEqualTo("John Doe")
        assertThat(msg.messageText).isEqualTo("See you at 6!")
        // Ensure it's scheduled ~1 hour from now (allowing some buffer for execution time)
        val diff = msg.scheduledTimestamp - System.currentTimeMillis()
        assertThat(diff).isGreaterThan(59 * 60_000L)
    }

    @Test
    fun testOutpostScreen_Composer_FilterChats() {
        // Arrange: Inject multiple Beeper chats
        val mockChat1 = BeeperChat("room1", "Alice", "WhatsApp")
        val mockChat2 = BeeperChat("room2", "Bob", "iMessage")
        GatekeeperStateManager.dispatch(GatekeeperAction.BeeperChatsLoaded(listOf(mockChat1, mockChat2)))

        composeTestRule.setContent {
            GatekeeperTheme {
                OutpostScreen()
            }
        }

        // Act: Type to filter
        composeTestRule.onNodeWithText("Select Chat").performTextInput("Bob")
        composeTestRule.waitForIdle()

        // Assert: Alice should not be visible, Bob should be
        composeTestRule.onNodeWithText("Bob (iMessage)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice (WhatsApp)").assertDoesNotExist()
    }

    @Test
    fun testOutpostScreen_QueueView_CancelsMessage() {
        // Arrange: Inject a scheduled message into state
        val msg =
            ScheduledMessage(
                id = "msg_123",
                beeperRoomId = "room1",
                chatName = "Wife",
                messageText = "Buy milk",
                scheduledTimestamp = System.currentTimeMillis() + 300_000L, // 5 mins
            )
        GatekeeperStateManager.dispatch(GatekeeperAction.ScheduleMessage(msg))

        composeTestRule.setContent {
            GatekeeperTheme {
                OutpostScreen()
            }
        }

        // Act: Switch to Queue Tab
        composeTestRule.onNodeWithText("Queue (1)").performClick()
        composeTestRule.waitForIdle()

        // Assert: Message is visible
        composeTestRule.onNodeWithText("Wife").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buy milk").assertIsDisplayed()

        // Act: Click Drop
        composeTestRule.onNodeWithText("Drop").performClick()
        composeTestRule.waitForIdle()

        // Assert: UI updates to empty state immediately because list is filtered by PENDING
        composeTestRule.onNodeWithText("Queue is empty.").assertIsDisplayed()

        // Assert: Underlying state status is updated
        val state = GatekeeperStateManager.state.value
        assertThat(
            state.scheduledMessages
                .first()
                .status.name,
                ).isEqualTo("CANCELLED")
    }

    @Test
    fun testOutpostScreen_Composer_FilterChats() {
        // Arrange: Inject multiple Beeper chats
        val mockChat1 = BeeperChat("room1", "Alice", "WhatsApp")
        val mockChat2 = BeeperChat("room2", "Bob", "iMessage")
        GatekeeperStateManager.dispatch(GatekeeperAction.BeeperChatsLoaded(listOf(mockChat1, mockChat2)))

        composeTestRule.setContent {
            GatekeeperTheme {
                OutpostScreen()
            }
        }

        // Act: Type to filter
        composeTestRule.onNodeWithText("Select Chat").performTextInput("Bob")
        composeTestRule.waitForIdle()

        // Assert: Alice should not be visible, Bob should be
        composeTestRule.onNodeWithText("Bob (iMessage)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice (WhatsApp)").assertDoesNotExist()
    }
}
