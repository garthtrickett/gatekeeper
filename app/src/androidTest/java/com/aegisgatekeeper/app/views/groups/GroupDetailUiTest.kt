package com.aegisgatekeeper.app.views.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupDetailUiTest {
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
    fun testDomainBlockSection_rendersAndOpensDialog() {
        val mockGroup =
            AppGroup(
                id = "test-group",
                name = "Test Group",
                rules =
                    listOf(
                        BlockingRule.DomainBlock(
                            id = "rule1",
                            groupId = "test-group",
                            domains = setOf("reddit.com"),
                        ),
                    ),
            )

        composeTestRule.setContent {
            GatekeeperTheme {
                GroupDetailScreen(group = mockGroup, onBack = {})
            }
        }

        // Verify the new standalone section exists
        composeTestRule.onNodeWithText("Blocked Domains:").assertIsDisplayed()
        composeTestRule.onNodeWithText("reddit.com").assertIsDisplayed()

        // Verify clicking Edit Domains opens the dialog
        composeTestRule.onNodeWithText("Edit Domains").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Domain Block").assertIsDisplayed() // Dialog title
    }

    @Test
    fun testRuleChoiceDialog_containsAllRuleTypes() {
        val mockGroup = AppGroup(id = "test-group", name = "Test Group")

        composeTestRule.setContent {
            GatekeeperTheme {
                GroupDetailScreen(group = mockGroup, onBack = {})
            }
        }

        // Open the general Add Rule dialog
        composeTestRule.onNodeWithText("+ Add Rule").performClick()
        composeTestRule.waitForIdle()

        // Verify All rule types are present
        composeTestRule.onNodeWithText("Select Rule Type").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily Time Limit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scheduled Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Strict Check-In").assertIsDisplayed()
                composeTestRule.onNodeWithText("Always Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Domain Block").assertDoesNotExist() // This is now in its own section

        // Close dialog to prevent Compose detach crashes during Activity destruction
        composeTestRule.onNodeWithText("Always Block").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAlwaysBlockRule_rendersCorrectlyInList() {
        val mockGroup =
            AppGroup(
                id = "test-group",
                name = "Test Group",
                rules =
                    listOf(
                        com.aegisgatekeeper.app.domain.BlockingRule.AlwaysBlock(
                            id = "rule1",
                            groupId = "test-group",
                        ),
                    ),
            )

        composeTestRule.setContent {
            GatekeeperTheme {
                GroupDetailScreen(group = mockGroup, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Always Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Blocked 24/7").assertIsDisplayed()
    }
}
