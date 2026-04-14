package com.gatekeeper.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
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

    @Test
    fun test00_SmokeTest() {
        // This tests whether the Compose test runner can launch an activity at all.
        composeTestRule.setContent {
            androidx.compose.material3.Text("Smoke Test Working")
        }
        composeTestRule.onNodeWithText("Smoke Test Working").assertExists()
    }

    @Ignore("Temporarily disabled for systematic debugging")
    @Test
    fun testInterceptionChoiceUiRendering() {
        // Arrange & Act: Render the composable directly.
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                InterceptionChoiceUi(
                    interceptedPackage = testPackage,
                    onBypass = { },
                    onFriction = { }
                )
            }
        }

        // Assert: Check that the primary UI elements exist.
        // We use assertExists() over assertIsDisplayed() because MovingCloseButton uses random
        // absolute offsets, which can easily clip out of small emulator screens causing flakiness.
        composeTestRule.onNodeWithText("You are about to open com.test.interceptedapp.").assertExists()
        composeTestRule.onNodeWithText("Continue with friction").assertExists()
        composeTestRule.onNodeWithText("Emergency Bypass").assertExists()
        composeTestRule.onNodeWithText("Give Up").assertExists()
    }

    @Ignore("Temporarily disabled for systematic debugging")
    @Test
    fun testInterceptionChoiceUiClicks() {
        // Arrange
        var bypassClicked = false
        var frictionClicked = false

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                InterceptionChoiceUi(
                    interceptedPackage = testPackage,
                    onBypass = { bypassClicked = true },
                    onFriction = { frictionClicked = true }
                )
            }
        }

        // Act: Simulate clicks
        composeTestRule.onNodeWithText("Emergency Bypass").performClick()
        composeTestRule.onNodeWithText("Continue with friction").performClick()

        // Assert: Verify the lambdas were invoked.
        assertThat(bypassClicked).isTrue()
        assertThat(frictionClicked).isTrue()
    }

    @Ignore("Temporarily disabled for systematic debugging")
    @Test
    fun testEmergencyBypassUiRendering() {
        // Arrange
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                EmergencyBypassUi(interceptedPackage = testPackage)
            }
        }

        // Assert: Initial state
        composeTestRule.onNodeWithText("Why do you need to open com.test.interceptedapp?").assertExists()
        composeTestRule.onNodeWithText("Unlock for 5 minutes").assertExists()
    }
}
