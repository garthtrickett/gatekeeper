package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BallBalancingUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testGauntlet_GiveUpCallback_Works() {
        var closeCalled = false
        var successCalled = false

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            BallBalancingUi(
                onSuccess = { successCalled = true },
                onClose = { closeCalled = true },
            )
        }

        composeTestRule.mainClock.advanceTimeBy(500)

        // The randomised close button uses text "Give Up"
        composeTestRule.onNodeWithText("Give Up").performClick()

        composeTestRule.mainClock.advanceTimeBy(500)

        assertThat(closeCalled).isTrue()
        assertThat(successCalled).isFalse()
    }
}
