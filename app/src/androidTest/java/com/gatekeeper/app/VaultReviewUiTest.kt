package com.gatekeeper.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.views.VaultReviewScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class VaultReviewUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testVaultReview_LockedOutsideWindow() {
        // Arrange: 5:00 PM is outside the 6:00 PM - 6:30 PM window
        val lockedTime = LocalTime.of(17, 0)

        // Act
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                VaultReviewScreen(overrideTime = lockedTime)
            }
        }

        // Assert
        composeTestRule.onNodeWithText("The Vault is Locked").assertExists()
        composeTestRule
            .onNodeWithText(
                "Your distractions are safely stored.\nYou can review them between 6:00 PM and 6:30 PM.",
            ).assertExists()
    }

    @Test
    fun testVaultReview_UnlockedInsideWindow() {
        // Arrange: 6:15 PM is inside the window
        val unlockedTime = LocalTime.of(18, 15)

        // Act
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                VaultReviewScreen(overrideTime = unlockedTime)
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Lookup Vault").assertExists()
        composeTestRule.onNodeWithText("You have until 6:30 PM to review these.").assertExists()
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
            androidx.compose.material3.MaterialTheme {
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
