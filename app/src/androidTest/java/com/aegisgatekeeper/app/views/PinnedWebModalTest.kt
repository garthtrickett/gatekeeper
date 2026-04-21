package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedWebModalTest {
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
    fun testModalRendersAndCloses() {
        var closed = false

        composeTestRule.setContent {
            GatekeeperTheme {
                PinnedWebModal(url = "https://example.com", onClose = { closed = true })
            }
        }

        // Assert the close button exists
        composeTestRule.onNodeWithText("Exit").assertExists()

        // Perform click to close
        composeTestRule.onNodeWithText("Exit").performClick()

        // Verify callback was triggered
        composeTestRule.waitForIdle()
        assertThat(closed).isTrue()
    }
}
