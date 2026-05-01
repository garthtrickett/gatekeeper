package com.aegisgatekeeper.app.views

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
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
    val composeTestRule =
        androidx.compose.ui.test.junit4
            .createAndroidComposeRule<com.aegisgatekeeper.app.MainActivity>()

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
        // We use reflection to set the state directly to avoid side-effects (like DB writes
        // from UpgradeToProTier or navigating to the home screen via LogGiveUp) that can
        // interfere with the test host Activity.
        val stateFlowField = GatekeeperStateManager.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow =
            stateFlowField.get(
                GatekeeperStateManager,
            ) as kotlinx.coroutines.flow.MutableStateFlow<com.aegisgatekeeper.app.domain.GatekeeperState>
        stateFlow.value = stateFlow.value.copy(isProTier = true, analyticsGiveUps = 1)

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
