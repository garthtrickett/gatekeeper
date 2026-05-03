package com.aegisgatekeeper.app.views

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.domain.MetacognitionRequest
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetacognitionDialogTest {
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
    fun testMetacognitionDialog_LogsEmotionAndDismisses() {
        var dismissed = false
        val request = MetacognitionRequest(packageName = "TestApp", durationMillis = 120000L)

        composeTestRule.setContent {
            GatekeeperTheme {
                MetacognitionDialog(
                    request = request,
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Was this worth it?").assertExists()
        composeTestRule.onNodeWithText("Happy").performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissed).isTrue()

        val state = GatekeeperStateManager.state.value
        assertThat(state.data.sessionLogs).hasSize(1)
        assertThat(
            state.data.sessionLogs
                .first()
                .packageName,
        ).isEqualTo("TestApp")
        assertThat(
            state.data.sessionLogs
                .first()
                .emotion,
        ).isEqualTo(Emotion.HAPPY)
    }
}
