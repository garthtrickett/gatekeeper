package com.aegisgatekeeper.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultCaptureUiTest {
    // We use MainActivity as the host to provide the necessary local window/context
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @org.junit.After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testVaultCaptureDialog_InputAndSave() {
        // Arrange
        var savedQuery: String? = null
        var dismissed = false

        composeTestRule.setContent {
            GatekeeperTheme {
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
    fun testVaultCaptureDialog_KeyboardDoneAction() {
        // Arrange
        var savedQuery: String? = null

        composeTestRule.setContent {
            GatekeeperTheme {
                VaultCaptureDialog(
                    onDismiss = { },
                    onSave = { savedQuery = it },
                )
            }
        }

        // Act: Type text into the text field
        composeTestRule.onNodeWithText("What do you want to search?").performTextInput("ergonomic mouse")

        // Act: Trigger the 'Done' action on the keyboard
        composeTestRule.onNodeWithText("What do you want to search?").performImeAction()

        // Assert: Verify the callback was triggered via keyboard action
        assertThat(savedQuery).isEqualTo("ergonomic mouse")
    }

    @Test
    fun testVaultCaptureDialog_DismissesProperly() {
        // Arrange
        var dismissed = false

        composeTestRule.setContent {
            GatekeeperTheme {
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
