package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsUiTest {
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
    fun testAnalytics_FreeTier_ShowsPaywall() {
        composeTestRule.setContent {
            GatekeeperTheme {
                AnalyticsScreen()
            }
        }

        composeTestRule.onNodeWithText("Pro Analytics & Export").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unlock Lifetime Pro - $129").assertIsDisplayed()
    }

    @Test
    fun testAnalytics_ProTier_ShowsMetrics() {
        GatekeeperStateManager.dispatch(GatekeeperAction.UpgradeToProTier)
        // The LogGiveUp action has a side-effect that navigates to the home screen,
        // which can interfere with the test host Activity. We use reflection to
        // set the state directly to avoid this.
        val stateFlowField = GatekeeperStateManager.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow =
            stateFlowField.get(
                GatekeeperStateManager,
            ) as kotlinx.coroutines.flow.MutableStateFlow<com.aegisgatekeeper.app.domain.GatekeeperState>
        stateFlow.value = stateFlow.value.copy(analyticsGiveUps = 1)

        composeTestRule.setContent {
            GatekeeperTheme {
                AnalyticsScreen()
            }
        }

        composeTestRule.onNodeWithText("Insights").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithText("~15 mins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export Data (Markdown/CSV)").assertIsDisplayed()
    }
}
