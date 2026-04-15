package com.gatekeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatekeeper.app.domain.ContentSource
import com.gatekeeper.app.domain.ContentType
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.views.ContentBankScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentBankUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testContentBank_EmptyState() {
        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                ContentBankScreen()
            }
        }

        composeTestRule.onNodeWithText("The Content Bank").assertExists()
        composeTestRule.onNodeWithText("Bank is empty. Share a link to Gatekeeper to capture it.").assertIsDisplayed()
    }

    @Test
    fun testContentBank_PopulatedState_RendersRanksAndTitles() {
        // Seed State
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                ContentBankScreen()
            }
        }

        // Verify item data renders
        composeTestRule.onNodeWithText("Never Gonna Give You Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("YOUTUBE").assertIsDisplayed()
        composeTestRule.onNodeWithText("#1").assertIsDisplayed() // Rank 0 renders as #1

        // The top item cannot be moved up
        composeTestRule.onNodeWithText("▲").assertIsNotEnabled()
        // With only 1 item, it also cannot be moved down
        composeTestRule.onNodeWithText("▼").assertIsNotEnabled()
    }

    @Test
    fun testContentBank_DropButton_RemovesItem() {
        val title = "Test Drop Video ${System.currentTimeMillis()}"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid123",
                title = title,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )

        composeTestRule.setContent {
            androidx.compose.material3.MaterialTheme {
                ContentBankScreen()
            }
        }

        composeTestRule.onNodeWithText(title).assertExists()

        // Act: Click Drop
        composeTestRule.onNodeWithText("Drop").performClick()
        composeTestRule.waitForIdle()

        // Assert
        composeTestRule.onNodeWithText(title).assertDoesNotExist()
    }
}
