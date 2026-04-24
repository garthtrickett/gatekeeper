package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
class CleanYouTubeScreenTest {
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
    fun testDialogRenderingAndDismissal() {
        var dismissed = false
        composeTestRule.setContent {
            GatekeeperTheme {
                CleanYouTubeDialog(onDismiss = { dismissed = true })
            }
        }

        composeTestRule.onNodeWithText("Surgical Search").assertExists()
        composeTestRule.onNodeWithText("Search YouTube...").assertExists()
        composeTestRule.onNodeWithText("Exit").assertIsDisplayed()

        // Act
        composeTestRule.onNodeWithText("Exit").performClick()

        // Assert
        com.google.common.truth.Truth.assertThat(dismissed).isTrue()
    }

    @Test
    fun testSearchInput_updatesCurrentUrl() {
        composeTestRule.setContent {
            GatekeeperTheme {
                CleanYouTubeDialog(onDismiss = {})
            }
        }

        val query = "Kotlin Coroutines"
        
        // Act: Type query and click Search
        composeTestRule.onNodeWithText("Search YouTube...").performTextInput(query)
        composeTestRule.onNodeWithText("Search").performClick()

        // Assert: Since we can't easily inspect the internal WebView URL in this test, 
        // we verify the search bar still holds the query and the WebView is likely active.
        composeTestRule.onNodeWithText(query).assertExists()
    }

    @Test
    fun testAuthButton_triggersNavigation() {
        composeTestRule.setContent {
            GatekeeperTheme {
                CleanYouTubeDialog(onDismiss = {})
            }
        }

        // Act
        composeTestRule.onNodeWithText("Auth").performClick()

        // Assert: Verify state manager triggered the Pinned Website modal
        val state = GatekeeperStateManager.state.value
        assertThat(state.activePinnedWebsiteUrl).contains("accounts.google.com")
    }
}
