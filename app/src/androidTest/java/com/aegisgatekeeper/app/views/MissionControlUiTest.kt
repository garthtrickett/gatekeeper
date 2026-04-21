package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class MissionControlUiTest {
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
    fun testMissionControl_EmptyState() {
        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        composeTestRule.onNodeWithText("Mission Control").assertIsDisplayed()
        composeTestRule.onNodeWithText("No shortcuts pinned. Add some essentials.").assertIsDisplayed()
    }

    @Test
    fun testMissionControl_DisplaysSafeAndRestrictedCategories() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val pm = context.packageManager

        // Dynamically find two installed apps on the test device to ensure they aren't filtered out by the package manager
        val installedApps =
            pm
                .getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }

        if (installedApps.size < 2) return

        val safeAppPkg = installedApps[0].packageName
        val restrictedAppPkg = installedApps[1].packageName

        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction
                .UpdateMissionControlApps(listOf(safeAppPkg, restrictedAppPkg)),
        )

        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.CreateAppGroup(
                id = "test-group",
                name = "Test Block Group",
                apps = setOf(restrictedAppPkg),
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Safe Apps").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the headers and the group label appear correctly
        composeTestRule.onNodeWithText("Safe Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restricted Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Block Group", substring = true).assertIsDisplayed()
    }

    @Test
    fun testMissionControl_OpensAppPicker() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val pm = context.packageManager
        val installedApps =
            pm
                .getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
                .sortedBy { pm.getApplicationLabel(it).toString() }

        if (installedApps.isEmpty()) return

        val firstAppName = pm.getApplicationLabel(installedApps.first()).toString()

        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        // Click the floating action button (identified by its text "+")
        composeTestRule.onNodeWithText("+").performClick()

        // Verify choice dialog appears
        composeTestRule.onNodeWithText("Add to Mission Control").assertIsDisplayed()

        // Click Add an App
        composeTestRule.onNodeWithText("Add an App").performClick()

        // Verify App Picker dialog appears
        composeTestRule.onNodeWithText("Pin Essential Apps").assertIsDisplayed()

        // Assert that the first installed app is present in the list
        composeTestRule.onNodeWithText(firstAppName).assertExists()
    }

    @Test
    fun testMissionControl_OpensAddWebsiteDialog() {
        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        // Click the floating action button
        composeTestRule.onNodeWithText("+").performClick()

        // Choose "Add a Website"
        composeTestRule.onNodeWithText("Add a Website").performClick()

        // Verify dialog appears
        composeTestRule.onNodeWithText("Pin a Website").assertIsDisplayed()
        composeTestRule.onNodeWithText("URL (e.g. https://...)").assertExists()
    }

    @Test
    fun testMissionControl_DisplaysPinnedWebsites() {
        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.AddPinnedWebsite(
                id = "site1",
                label = "Hacker News",
                url = "https://news.ycombinator.com",
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pinned Websites").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Pinned Websites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hacker News").assertIsDisplayed()
    }

    @Test
    fun testMissionControl_ShowsSurgicalFacebookButtons() {
        composeTestRule.setContent {
            GatekeeperTheme {
                MissionControlScreen()
            }
        }

        // Assert: Section header exists
        composeTestRule.onNodeWithText("Surgical Utilities").assertIsDisplayed()

        // Assert: Launch buttons exist
        composeTestRule.onNodeWithText("FB Groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("FB Events").assertIsDisplayed()

        // Act: Click FB Groups
        composeTestRule.onNodeWithText("FB Groups").performClick()

        // Assert: State updated correctly
        val state = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(state.activeFacebookUrl)
            .contains("/groups/")
    }
}
