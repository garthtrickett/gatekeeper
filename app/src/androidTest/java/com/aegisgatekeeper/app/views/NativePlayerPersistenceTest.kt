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

        // 1. Setup Content Bank
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveToContentBank(
            videoId = item.videoId,
            title = item.title,
            source = item.source,
            type = item.type,
            currentTimestamp = item.capturedAtTimestamp
        ))

        // 2. SEED THE POSITION FIRST - This simulates a returning user
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(podcastUrl, 5f))

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

        // 3. Open the player initially - should start at 00:05 due to seeding
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
        
        // Wait for MediaController to connect and sync (can take a moment in tests)
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNodeWithText("00:05").assertExists()
                true
            } catch (e: Throwable) { false }
        }

        // 4. Minimize and re-open to trigger the "Re-attach" logic branch
        GatekeeperStateManager.dispatch(GatekeeperAction.MinimizeNativePlayer)
        composeTestRule.waitForIdle()
        
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
        composeTestRule.waitForIdle()

        // 5. Assert: Still at 00:05 (or at least not reset to 00:00)
        composeTestRule.onNodeWithText("00:05").assertExists()
    }
}