package com.aegisgatekeeper.app.views

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
import android.util.Log
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

        @Before
    fun setup() {
        Log.d("GatekeeperTest", "--> setup: START")
        GatekeeperStateManager.resetStateForTest()
        Log.d("GatekeeperTest", "--> setup: END")
    }

        @After
    fun tearDown() {
        Log.d("GatekeeperTest", "--> tearDown: START")
        GatekeeperStateManager.resetStateForTest()
        Log.d("GatekeeperTest", "--> tearDown: END")
    }

        @Test
    fun testAnalytics_FreeTier_ShowsPaywall() {
        Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: START")
        try {
            Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: CALLING setContent")
            composeTestRule.setContent {
                Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: INSIDE setContent")
                GatekeeperTheme {
                    Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: INSIDE GatekeeperTheme")
                    AnalyticsScreen()
                }
            }
            Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: setContent COMPLETED")
        } catch (e: Throwable) {
            Log.e("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: EXCEPTION in setContent", e)
            throw e
        }

        Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: ASSERTING")
        composeTestRule.onNodeWithText("Pro Analytics & Export").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unlock Lifetime Pro - $129").assertIsDisplayed()
        Log.d("GatekeeperTest", "--> testAnalytics_FreeTier_ShowsPaywall: END")
    }

            @Test
    fun testAnalytics_ProTier_ShowsMetrics() {
        Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: START")
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

        try {
            Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: CALLING setContent")
            composeTestRule.setContent {
                Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: INSIDE setContent")
                GatekeeperTheme {
                    Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: INSIDE GatekeeperTheme")
                    AnalyticsScreen()
                }
            }
            Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: setContent COMPLETED")
        } catch (e: Throwable) {
            Log.e("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: EXCEPTION in setContent", e)
            throw e
        }

        Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: ASSERTING")
        composeTestRule.onNodeWithText("Insights").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithText("~15 mins").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export Data (Markdown/CSV)").assertIsDisplayed()
        Log.d("GatekeeperTest", "--> testAnalytics_ProTier_ShowsMetrics: END")
    }
}
