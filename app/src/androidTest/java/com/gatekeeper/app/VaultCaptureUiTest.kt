package com.gatekeeper.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultCaptureUiTest {
    // We use MainActivity as the host to provide the necessary local window/context
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testVaultCaptureDialog_InputAndSave() {
        // Arrange
        var savedQuery: String? = null
        var dismissed = false

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                VaultCaptureDialog(
                    onDismiss = { dismissed = true },
                    onSave = { savedQuery = it },
                )
            }
        }

        // Assert initial UI state
        composeTestRule.onNodeWithText("Lookup Vault").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()

        // Act: Type text into the text field
        composeTestRule.onNodeWithText("What do you want to search?").performTextInput("mechanical keyboards")

        // Act: Click save
        composeTestRule.onNodeWithText("Save").performClick()

        // Assert: Verify the callback was triggered with the correct text
        assertThat(savedQuery).isEqualTo("mechanical keyboards")
    }

    @Test
    fun testVaultCaptureDialog_DismissesProperly() {
        // Arrange
        var dismissed = false

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                VaultCaptureDialog(
                    onDismiss = { dismissed = true },
                    onSave = { },
                )
            }
        }

        // Act: Click cancel
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Assert: Verify the dismiss callback fired
        assertThat(dismissed).isTrue()
    }
}
