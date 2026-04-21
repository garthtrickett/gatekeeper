package com.aegisgatekeeper.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests verifying Activity-level routing, such as deep links from the Sovereignty Widget.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @After
    fun tearDown() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun testWidgetDeepLink_DispatchesOpenCleanPlayerAction() {
        // Arrange: Simulate the exact intent fired by the Glance Widget's "Watch" button
        val testVideoId = "abc123XYZ"
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                putExtra("OPEN_CLEAN_PLAYER_VIDEO_ID", testVideoId)
            }

        // Act: Launch the Activity with the specific intent
        ActivityScenario.launch<MainActivity>(intent).use {
            // Assert: The intent should have been intercepted in onCreate(),
            // dispatching OpenCleanPlayer and setting the activeVideoId in the StateManager.
            val currentState = GatekeeperStateManager.state.value
            assertThat(currentState.activeVideoId).isEqualTo(testVideoId)
        }
    }
}
