package com.aegisgatekeeper.app.views

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.resetStateForTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePlayerPersistenceTest {
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
    fun testNativePlayer_MaintainsPosition_OnUiReentry() {
        val podcastUrl = "https://example.com/audio.mp3"
        val item = com.aegisgatekeeper.app.domain.ContentItem(
            id = "test_ep",
            videoId = podcastUrl,
            title = "Persistence Test",
            source = ContentSource.GENERIC,
            type = ContentType.AUDIO,
            rank = 0,
            capturedAtTimestamp = System.currentTimeMillis()
        )

        // 1. Seed state with the item and show the player
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveToContentBank(
            videoId = item.videoId,
            title = item.title,
            source = item.source,
            type = item.type,
            currentTimestamp = item.capturedAtTimestamp
        ))

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                if (state.activeNativeMediaItem != null) {
                    NativeAudioPlayerModal(
                        contentItem = state.activeNativeMediaItem!!,
                        isVisible = state.isNativeAudioPlayerModalVisible,
                        onMinimize = { GatekeeperStateManager.dispatch(GatekeeperAction.MinimizeNativePlayer) },
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseNativePlayer) }
                    )
                }
            }
        }

        // 2. Open the player
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
        composeTestRule.waitForIdle()
        
        // 3. Simulate position moving to 00:05 (via state or just waiting)
        // We manually inject a saved position into the state manager to simulate "existing playback"
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(podcastUrl, 5f))

        // 4. "Minimize" the player (hides the Composable)
        GatekeeperStateManager.dispatch(GatekeeperAction.MinimizeNativePlayer)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("End Session").assertDoesNotExist()

        // 5. "Restore" the player (re-initializes the Composable)
        // This triggers the logic we just fixed
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
        composeTestRule.waitForIdle()

        // 6. Assert: The timer should NOT show 00:00 immediately.
        // If it reset, it would flick to 00:00. If it re-attached correctly, it should show 00:05
        composeTestRule.onNodeWithText("00:05").assertExists()
    }
}