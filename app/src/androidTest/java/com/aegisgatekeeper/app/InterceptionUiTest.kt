package com.aegisgatekeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.views.interception.EmergencyBypassUi
import com.aegisgatekeeper.app.views.interception.InterceptionChoiceUi
import com.aegisgatekeeper.app.views.interception.TimeBoxSwapUi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the interception composables.
 * Since the UI is rendered in a WindowManager overlay, not an Activity, we cannot test
 * the full end-to-end flow easily. Instead, we test each screen in isolation
 * by rendering it directly onto a test-owned Compose surface.
 */
@RunWith(AndroidJUnit4::class)
class InterceptionUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testPackage = "com.test.interceptedapp"

    @org.junit.Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @org.junit.After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testInterceptionChoiceUiRendering() {
        // Arrange & Act: Render the composable directly.
        composeTestRule.setContent {
            GatekeeperTheme {
                InterceptionChoiceUi(
                    interceptedPackage = testPackage,
                    contentItems = emptyList(),
                    onBypass = { _ -> },
                    onFriction = { _ -> },
                )
            }
        }

        // Assert: Check that the primary UI elements exist on the initial prompt screen.
        composeTestRule.onNodeWithText("Take a breath.").assertExists()
        composeTestRule.onNodeWithText("You are about to open Interceptedapp.").assertExists()
        composeTestRule.onNodeWithText("Consume curated content instead").assertExists()
        composeTestRule.onNodeWithText("Choose a positive habit").assertExists()
        composeTestRule.onNodeWithText("Continue to Interceptedapp").assertExists()
        composeTestRule.onNodeWithText("Give Up").assertExists()
    }

    @Test
    fun testInterceptionChoiceUi_ProactiveSuggestion_FiltersAndPlays() {
        var playedItem: com.aegisgatekeeper.app.domain.ContentItem? = null

        val mockItems =
            listOf(
                com.aegisgatekeeper.app.domain.ContentItem(
                    id = "1",
                    videoId = "v1",
                    title = "30 Min Video",
                    source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                    type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                    rank = 1,
                    capturedAtTimestamp = 0,
                    durationSeconds = 1800L, // 30 mins
                ),
                com.aegisgatekeeper.app.domain.ContentItem(
                    id = "2",
                    videoId = "v2",
                    title = "10 Min Video",
                    source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                    type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                    rank = 2,
                    capturedAtTimestamp = 0,
                    durationSeconds = 600L, // 10 mins
                ),
            )

        composeTestRule.setContent {
            GatekeeperTheme {
                InterceptionChoiceUi(
                    interceptedPackage = testPackage,
                    contentItems = mockItems,
                    onBypass = { _ -> },
                    onFriction = { _ -> },
                    onPlayContent = { item -> playedItem = item },
                )
            }
        }

        // Navigate to Curated Content step
        composeTestRule.onNodeWithText("Consume curated content instead").performClick()
        composeTestRule.waitForIdle()

        // Default is "10-30m" (601..1800s), so 30 Min Video (1800s) fits
        composeTestRule.onNodeWithText("30 Min Video").assertExists()
        composeTestRule.onNodeWithText("10 Min Video").assertDoesNotExist()

        // Act: Click "5-10m" chip (301..600s)
        composeTestRule.onNodeWithText("5-10m").performClick()
        composeTestRule.waitForIdle()

        // Assert: 30m video is excluded, 10 Min Video (600s) fits
        composeTestRule.onNodeWithText("10 Min Video").assertExists()
        composeTestRule.onNodeWithText("30 Min Video").assertDoesNotExist()

        // Act: Click the suggested content card
        composeTestRule.onNodeWithText("10 Min Video").performClick()
        composeTestRule.waitForIdle()

        // Assert: The callback was triggered with the correct item
        assertThat(playedItem?.title).isEqualTo("10 Min Video")
    }

    @Test
    fun testInterceptionChoiceUi_CustomMessageRendering() {
        val customMessage = "Do we really need to check Beeper again?"
        composeTestRule.setContent {
            GatekeeperTheme {
                InterceptionChoiceUi(
                    interceptedPackage = "com.test.beeper",
                    customMessage = customMessage,
                    contentItems = emptyList(),
                    onBypass = { _ -> },
                    onFriction = { _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText(customMessage).assertExists()
        composeTestRule.onNodeWithText("You are about to open Beeper.").assertExists()
        // Ensure default message is not there
        composeTestRule.onNodeWithText("Take a breath.").assertDoesNotExist()
    }

    @Test
    fun testInterceptionChoiceUiClicks() {
        // Arrange
        var bypassClicked = false
        var frictionClicked = false

        composeTestRule.setContent {
            GatekeeperTheme {
                InterceptionChoiceUi(
                    interceptedPackage = testPackage,
                    contentItems = emptyList(),
                    onBypass = { _ -> bypassClicked = true },
                    onFriction = { _ -> frictionClicked = true },
                )
            }
        }

        // Navigate to Unlock step
        composeTestRule.onNodeWithText("Continue to Interceptedapp").performClick()
        composeTestRule.waitForIdle()

        // Act: Simulate clicks
        composeTestRule.onNodeWithText("Emergency Bypass").performClick()
        composeTestRule.onNodeWithText("Continue with friction").performClick()

        // Assert: Verify the lambdas were invoked.
        assertThat(bypassClicked).isTrue()
        assertThat(frictionClicked).isTrue()
    }

    @Test
    fun testInterceptionChoiceUi_NavigateToHabits_AndSelect() {
        // Arrange: Seed state with a habit
        val habitDesc = "Doing Pushups"
        val testPkg = "com.test.app"

        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.AddAlternativeActivity(habitDesc, System.currentTimeMillis())
        )

        // CRITICAL: Trigger an interception so InterceptionScreen doesn't return early
        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.RuleViolationDetected(testPkg, "Test Reason", System.currentTimeMillis())
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                // We test the top-level InterceptionScreen which handles the navigation logic
                com.aegisgatekeeper.app.views.interception.InterceptionScreen()
            }
        }

        // 1. Click "Choose a positive habit"
        composeTestRule.onNodeWithText("Choose a positive habit").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify habit list is shown
        composeTestRule.onNodeWithText("Do one of these instead:").assertExists()
        composeTestRule.onNodeWithText(habitDesc).assertIsDisplayed()

        // 3. Select the habit
        composeTestRule.onNodeWithText(habitDesc).performClick()
        composeTestRule.waitForIdle()

        // 4. Verify state: Moat should be closed (Dismissed) and GiveUp logged
        assertThat(GatekeeperStateManager.state.value.isOverlayActive).isFalse()
        assertThat(GatekeeperStateManager.state.value.analyticsGiveUps).isEqualTo(1)
    }

    @Test
    fun testEmergencyBypassUiRendering() {
        // Arrange
        composeTestRule.setContent {
            GatekeeperTheme {
                EmergencyBypassUi(
                    interceptedPackage = testPackage,
                    allocatedTimeMillis = 300_000L,
                )
            }
        }

        // Assert: Initial state
        composeTestRule.onNodeWithText("Why do you need to open Interceptedapp?").assertExists()
        composeTestRule.onNodeWithText("Unlock for 5 minutes").assertExists()
    }

    @Test
    fun testTimeBoxSwapUiRendering_WithValidContent() {
        val mockItems =
            listOf(
                com.aegisgatekeeper.app.domain.ContentItem(
                    id = "1",
                    videoId = "v1",
                    title = "Focus Video",
                    source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                    type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                    rank = 0,
                    capturedAtTimestamp = 0,
                    durationSeconds = 300L, // 5 mins
                ),
            )

        composeTestRule.setContent {
            GatekeeperTheme {
                TimeBoxSwapUi(
                    maxMinutes = 10,
                    items = mockItems,
                    onPlayVideo = { },
                    onPlayAudio = { },
                    onOpenLink = { },
                    onCancel = { },
                )
            }
        }

        composeTestRule.onNodeWithText("Intentional Swap").assertExists()
        composeTestRule.onNodeWithText("Watch this instead:").assertExists()
        composeTestRule.onNodeWithText("Focus Video").assertExists()
        composeTestRule.onNodeWithText("⏱️ 5m").assertExists()
    }

    @Test
    fun testTimeBoxSwapUiRendering_EmptyState() {
        composeTestRule.setContent {
            GatekeeperTheme {
                TimeBoxSwapUi(
                    maxMinutes = 10,
                    items = emptyList(),
                    onPlayVideo = { },
                    onPlayAudio = { },
                    onOpenLink = { },
                    onCancel = { },
                )
            }
        }

        composeTestRule.onNodeWithText("No saved content under 10 minutes.").assertExists()
        composeTestRule.onNodeWithText("Don't fill the void with junk. Breathe, or give up.").assertExists()
    }
}
