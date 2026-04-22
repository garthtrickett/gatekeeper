package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class AlternativeActivitiesUiTest {
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
    fun testHabitsManagement_AddAndRemove() {
        composeTestRule.setContent {
            GatekeeperTheme {
                AlternativeActivitiesScreen()
            }
        }

        val habitDescription = "Read 10 pages of a book"

        // 1. Add a habit
        composeTestRule.onNodeWithText("e.g. Read a book").performTextInput(habitDescription)
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify it appears in the list
        composeTestRule.onNodeWithText(habitDescription).assertIsDisplayed()
        assertThat(GatekeeperStateManager.state.value.alternativeActivities.any { it.description == habitDescription }).isTrue()

        // 3. Remove the habit
        composeTestRule.onNodeWithText("🗑️").performClick()
        composeTestRule.waitForIdle()

        // 4. Verify it is gone
        composeTestRule.onNodeWithText(habitDescription).assertDoesNotExist()
        assertThat(GatekeeperStateManager.state.value.alternativeActivities).isEmpty()
    }
}
