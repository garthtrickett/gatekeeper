package com.gatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeeper.app.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleanPlayerModalTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testModalRendersAndCloses() {
        var closed = false

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                CleanPlayerModal(videoId = "test1234", onClose = { closed = true })
            }
        }

        // Assert the close button exists
        composeTestRule.onNodeWithText("Close Video").assertExists()

        // Perform click
        composeTestRule.onNodeWithText("Close Video").performClick()

        // Verify callback was triggered
        assertThat(closed).isTrue()
    }
}
