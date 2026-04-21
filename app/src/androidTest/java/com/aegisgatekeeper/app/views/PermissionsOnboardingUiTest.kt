package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class PermissionsOnboardingUiTest {
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
    fun testOnboarding_StartsAtStep1() {
        composeTestRule.setContent {
            GatekeeperTheme {
                PermissionsOnboardingScreen()
            }
        }

        // Assert: Initial state shows Step 1
        composeTestRule.onNodeWithText("STEP 1 OF 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Iron Gate").assertIsDisplayed()
    }

    @Test
    fun testOnboarding_AutoAdvancesToStep2_WhenOverlayGranted() {
        composeTestRule.setContent {
            GatekeeperTheme {
                PermissionsOnboardingScreen()
            }
        }

        // Act: Dispatch state update showing the first permission is granted
        GatekeeperStateManager.dispatch(
            GatekeeperAction.PermissionsUpdated(
                hasOverlay = true,
                hasUsageAccess = false,
                hasAccessibility = false,
                isBatteryDisabled = false,
            ),
        )

        // Assert: Wait for Compose re-render and animation, check Step 2
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STEP 2 OF 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("App Tracking").assertIsDisplayed()
    }

    @Test
    fun testOnboarding_AutoAdvancesToFinalBoss_WhenFirstThreeGranted() {
        composeTestRule.setContent {
            GatekeeperTheme {
                PermissionsOnboardingScreen()
            }
        }

        // Act: Dispatch state update showing the first three are granted
        GatekeeperStateManager.dispatch(
            GatekeeperAction.PermissionsUpdated(
                hasOverlay = true,
                hasUsageAccess = true,
                hasAccessibility = true,
                isBatteryDisabled = false,
            ),
        )

        // Assert: Pager should skip straight to the Battery exclusion page
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STEP 4 OF 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Final Boss").assertIsDisplayed()
    }
}
