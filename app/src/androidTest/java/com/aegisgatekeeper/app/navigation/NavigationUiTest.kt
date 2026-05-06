package com.aegisgatekeeper.app.navigation

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
class NavigationUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
        // Upgrade to Pro to ensure tabs aren't locked behind paywalls
        GatekeeperStateManager.dispatch(GatekeeperAction.UpgradeToProTier)
    }

    @After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testTabNavigation_SwitchesScreensCorrectly() {
        // Act: Render the main navigation host (Voyager TabNavigator)
        composeTestRule.setContent {
            GatekeeperTheme {
                MainNavigationScreen()
            }
        }

        // Assert: HomeTab (Mission Control) is the default active tab
        composeTestRule.onNodeWithText("Mission Control").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Content Bank").assertDoesNotExist()

        // Act: Click the 'Bank' tab in the bottom navigation bar
        composeTestRule.onNodeWithText("Bank").performClick()
        composeTestRule.waitForIdle()

        // Assert: The Content Bank screen is now visible
        composeTestRule.onNodeWithText("The Content Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mission Control").assertDoesNotExist()

        // Act: Click the 'Account' tab
        composeTestRule.onNodeWithText("Account").performClick()
        composeTestRule.waitForIdle()

        // Assert: The Account screen is now visible (Settings is part of AccountScreen)
        composeTestRule.onNodeWithText("Phase Boundaries").assertIsDisplayed()
    }

    @Test
    fun testGlobalModals_RenderOverNavigation() {
        // Arrange: Render the navigator
        composeTestRule.setContent {
            GatekeeperTheme {
                MainNavigationScreen()
            }
        }

        // Act: Dispatch an action to trigger a global modal (e.g., Clean Audio Player)
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanAudioPlayer("https://soundcloud.com/test"))
        composeTestRule.waitForIdle()

        // Assert: The modal should be injected over the navigator UI
        // We look for the 'Minimize' button which is part of the CleanAudioPlayerModal
        composeTestRule.onNodeWithText("Minimize").assertIsDisplayed()
        composeTestRule.onNodeWithText("End Session").assertIsDisplayed()
    }
}
