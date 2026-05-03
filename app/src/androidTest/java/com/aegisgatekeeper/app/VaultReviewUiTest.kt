package com.aegisgatekeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.views.VaultReviewScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class VaultReviewUiTest {
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
    fun testVaultReview_LockedOutsideWindow() {
        // Arrange: 5:00 PM is outside the 6:00 PM - 6:30 PM window
        val lockedTime = LocalTime.of(17, 0)

        // Act
        composeTestRule.setContent {
            GatekeeperTheme {
                VaultReviewScreen(overrideTime = lockedTime)
            }
        }

        // Assert
        composeTestRule.onNodeWithText("You are in Focus Mode").assertExists()
        composeTestRule
            .onNodeWithText(
                "Your thoughts are captured.\nThe Gathering Phase begins at 6:00 PM.",
            ).assertExists()

        // Step 1 check: Verify capture UI exists even when locked
        composeTestRule.onNodeWithText("Dump a thought...").assertExists()
        composeTestRule.onNodeWithText("Save to Vault").assertExists()
    }

    @Test
    fun testVaultReview_LockedState_CanStillCapture() {
        val lockedTime = LocalTime.of(17, 0)
        val testQuery = "In-app locked capture test"

        composeTestRule.setContent {
            GatekeeperTheme {
                VaultReviewScreen(overrideTime = lockedTime)
            }
        }

        // Act: Type and save
        composeTestRule.onNodeWithText("Dump a thought...").performTextInput(testQuery)
        composeTestRule.onNodeWithText("Save to Vault").performClick()
        composeTestRule.waitForIdle()

        // Assert: Verify action reached the StateManager
        val state = GatekeeperStateManager.state.value
        val item = state.data.vaultItems.find { it.query == testQuery }
        com.google.common.truth.Truth
            .assertThat(item)
            .isNotNull()

        // Assert: Verify input is cleared after save and confirmation is shown
        composeTestRule.onNodeWithText(testQuery).assertDoesNotExist()
        composeTestRule.onNodeWithText("SAVED ✓").assertIsDisplayed()

        // Assert: Verify confirmation disappears after a delay
        composeTestRule.mainClock.advanceTimeBy(2500) // Wait for the 2-second confirmation to pass
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SAVED ✓").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save to Vault").assertIsDisplayed()
    }

    @Test
    fun testVaultReview_UnlockedInsideWindow() {
        // Arrange: 6:15 PM is inside the window
        val unlockedTime = LocalTime.of(18, 15)

        // Act
        composeTestRule.setContent {
            GatekeeperTheme {
                VaultReviewScreen(overrideTime = unlockedTime)
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Lookup Vault").assertExists()
        composeTestRule.onNodeWithText("The Gathering Phase ends at 6:30 PM. Review your captured thoughts.").assertExists()
    }

    @Test
    fun testVaultReview_TriageButtons_TriggerCorrectActions() {
        // Arrange: Inside the time window
        val unlockedTime = LocalTime.of(18, 15)
        val query = "Test Triage Workflow"

        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToVault(query, System.currentTimeMillis()),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                VaultReviewScreen(overrideTime = unlockedTime)
            }
        }

        // Verify the item is displayed
        composeTestRule.onNodeWithText(query).assertExists()

        // Verify triage buttons
        composeTestRule.onAllNodesWithText("🌐 Web")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("🎬 YouTube")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("🎙️ Podcasts")[0].assertIsDisplayed()

        // Act: Click YouTube
        composeTestRule.onAllNodesWithText("🎬 YouTube")[0].performClick()
        composeTestRule.waitForIdle()

        // Assert: Verify state triggered the Web navigation
        val state = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(state.media.currentSurgicalUrl)
            .contains("m.youtube.com")

        // Assert: Item should be resolved
        val item = state.data.vaultItems.find { it.query == query }
        com.google.common.truth.Truth
            .assertThat(item?.isResolved)
            .isTrue()
    }

    @Test
    fun testVaultReview_DisplaysItemsAndResolves() {
        // Arrange: Inside the time window
        val unlockedTime = LocalTime.of(18, 15)
        val uniqueQuery = "Test Resolution UI ${System.currentTimeMillis()}"

        // Seed the state manager with a test query
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToVault(uniqueQuery, System.currentTimeMillis()),
        )

        // Act
        composeTestRule.setContent {
            GatekeeperTheme {
                VaultReviewScreen(overrideTime = unlockedTime)
            }
        }

        // Assert: The unique item appears in the list
        composeTestRule.onNodeWithText(uniqueQuery).assertExists()

        // Act: Our items are sorted by DESC, so [0] gets the one we literally just inserted above.
        composeTestRule.onAllNodesWithText("Resolved")[0].performClick()

        // Assert: Wait for compose state to update and assert the item is removed from the UI.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(uniqueQuery).assertDoesNotExist()
    }
}
