package com.aegisgatekeeper.app.views

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurgicalFacebookUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
        // Ensure a clean cookie state before each test to prevent login state from leaking.
        android.webkit.CookieManager
            .getInstance()
            .removeAllCookies(null)
        android.webkit.CookieManager
            .getInstance()
            .flush()
    }

    @After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testFacebookOverlay_AppearsAndCloses() {
        // Arrange: Open the Facebook Surgical Screen
        val testUrl = "https://m.facebook.com/groups/"
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook(testUrl))

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                if (state.activeFacebookUrl != null) {
                    SurgicalFacebookScreen(
                        url = state.activeFacebookUrl!!,
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalFacebook) },
                    )
                }
            }
        }

        // Assert: Screen components exist (Navigation buttons)
        composeTestRule.onNodeWithText("Groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Events").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logout").assertDoesNotExist()

        // Act: Click Exit
        composeTestRule.onNodeWithText("Exit").performClick()
        composeTestRule.waitForIdle()

        // Assert: URL is cleared in state manager
        assertThat(GatekeeperStateManager.state.value.activeFacebookUrl).isNull()
        composeTestRule.onNodeWithText("Exit").assertDoesNotExist()
    }

    @Test
    fun testFacebookSurgicalView_WithMockLogin_ShowsSurgicalControls() {
        // Arrange: Inject a dummy cookie so the screen thinks we are logged in
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie("https://m.facebook.com", "c_user=12345; xs=mock_session_secret")

        GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/"))

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                if (state.activeFacebookUrl != null) {
                    SurgicalFacebookScreen(
                        url = state.activeFacebookUrl!!,
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalFacebook) },
                    )
                }
            }
        }

        // Assert: Because we mocked the cookie, the logic should bypass LoginWebView and show GROUPS/EVENTS buttons
        composeTestRule.onNodeWithText("Groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()

        // Cleanup
        cookieManager.removeAllCookies(null)
    }

    @Test
    fun testFacebookScreen_TabSwitching_UpdatesStateAndButtonEnabledStatus() {
        // Arrange: Mock login state by injecting a cookie
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie("https://m.facebook.com", "c_user=12345; xs=mock_session_secret")

        // Start at Groups
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/"))

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                if (state.activeFacebookUrl != null) {
                    SurgicalFacebookScreen(
                        url = state.activeFacebookUrl!!,
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalFacebook) },
                    )
                }
            }
        }

        // Assert: Groups button is disabled because we are already there
        // Note: In IndustrialButton, enabled=false dims the button but text remains
        composeTestRule.onNodeWithText("Groups").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Events").assertIsEnabled()

        // Act: Click Events
        composeTestRule.onNodeWithText("Events").performClick()
        composeTestRule.waitForIdle()

        // Assert: State updated to Events URL
        val state = GatekeeperStateManager.state.value
        assertThat(state.activeFacebookUrl).contains("/events/")

        // Assert: Buttons flipped enabled status
        composeTestRule.onNodeWithText("Groups").assertIsEnabled()
        composeTestRule.onNodeWithText("Events").assertIsNotEnabled()
    }
}
