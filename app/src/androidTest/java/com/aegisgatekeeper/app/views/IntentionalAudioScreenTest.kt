package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentionalAudioScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
        GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.UpgradeToProTier)
    }

    @After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testRenders5EmptySlotsInitially() {
        composeTestRule.setContent {
            GatekeeperTheme {
                IntentionalContentScreen()
            }
        }

        composeTestRule.onNodeWithText("Intentional Slots").assertIsDisplayed()

        // Verify all 5 slots render as empty
        composeTestRule.onNodeWithText("Empty Slot 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Empty Slot 5").assertIsDisplayed()

        // Verify there are exactly 5 "Insert" buttons
        val insertNodes = composeTestRule.onAllNodesWithText("Insert")
        insertNodes.fetchSemanticsNodes().size.let { size ->
            assert(size == 5) { "Expected 5 Insert buttons, found $size" }
        }
    }

    @Test
    fun testInsertButton_TriggersFrictionGate() {
        composeTestRule.setContent {
            GatekeeperTheme {
                IntentionalContentScreen()
            }
        }

        // Disable auto-advance to stop the gyroscope BallBalancing game loop
        // from instantly auto-winning in the test environment.
        composeTestRule.mainClock.autoAdvance = false

        // Act: Click the very first "Insert" button
        composeTestRule.onAllNodesWithText("Insert")[0].performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        // Assert: The Friction Dialog overlays the screen
        composeTestRule.onNodeWithText("Unlock Slot").assertExists()

        // Close it so teardown doesn't hang
        composeTestRule.onNodeWithText("Give Up").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        // Cleanup
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun testRendersPopulatedSlot() {
        // Arrange: Populate Slot 3 (Index 2)
        // First, save the item to the Content Bank so it exists in the DB (satisfying the Foreign Key constraint)
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "v1",
                title = "Deep Focus Ambient",
                source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                currentTimestamp = 0L,
            ),
        )

        // Wait for background coroutine to finish DB insertion to satisfy Foreign Key constraint
        Thread.sleep(500)

        // Retrieve the fully formed item (with DB-generated UUID) from the state
        val savedItem =
            GatekeeperStateManager.state.value.contentItems
                .first { it.title == "Deep Focus Ambient" }

        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveIntentionalSlot(2, savedItem),
        )

        // Wait for background slot insertion
        Thread.sleep(500)

        composeTestRule.setContent {
            GatekeeperTheme {
                IntentionalContentScreen()
            }
        }

        // Assert: The new title is displayed
        composeTestRule.onNodeWithText("Deep Focus Ambient").assertIsDisplayed()
        // Assert: The "Insert" button became "Eject" for this populated slot
        composeTestRule.onNodeWithText("Eject").assertIsDisplayed()
    }
}
