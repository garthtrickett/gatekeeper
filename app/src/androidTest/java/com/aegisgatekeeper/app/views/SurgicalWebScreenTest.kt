package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurgicalWebScreenTest {
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
    fun testInitialRendering() {
        composeTestRule.setContent {
            GatekeeperTheme {
                SurgicalWebScreen()
            }
        }

        composeTestRule.onNodeWithText("Surgical URL").assertExists()
        composeTestRule.onNodeWithText("Go").assertExists()
    }

    @Test
    fun testUrlAutoPrefixing_AppendsHttpsWhenMissing() {
        composeTestRule.setContent {
            GatekeeperTheme {
                SurgicalWebScreen()
            }
        }

        // Act: Type a raw domain
        composeTestRule.onNodeWithText("Surgical URL").performTextClearance()
        composeTestRule.onNodeWithText("Surgical URL").performTextInput("news.ycombinator.com")

        // Click Go
        composeTestRule.onNodeWithText("Go").performClick()
        composeTestRule.waitForIdle()

        // Assert: The StateManager should have received the URL with https:// prepended
        val currentState = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(currentState.currentSurgicalUrl)
            .isEqualTo("https://news.ycombinator.com")
    }

    @Test
    fun testUrlAutoPrefixing_IgnoresExistingHttpPrefix() {
        composeTestRule.setContent {
            GatekeeperTheme {
                SurgicalWebScreen()
            }
        }

        // Act: Type a domain that already has a scheme
        composeTestRule.onNodeWithText("Surgical URL").performTextClearance()
        composeTestRule.onNodeWithText("Surgical URL").performTextInput("http://insecure-site.com")

        // Click Go
        composeTestRule.onNodeWithText("Go").performClick()
        composeTestRule.waitForIdle()

        // Assert: The StateManager should leave the http:// scheme intact
        val currentState = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(currentState.currentSurgicalUrl)
            .isEqualTo("http://insecure-site.com")
    }
}
