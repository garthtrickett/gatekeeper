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
    fun testNativePlayer_PreservesPosition_OnErrorState() {
        val podcastUrl = "https://example.com/broken_stream_${System.currentTimeMillis()}.mp3"
        val item =
            com.aegisgatekeeper.app.domain.ContentItem(
                id = "test_ep",
                videoId = podcastUrl,
                title = "Network Drop Test",
                source = ContentSource.GENERIC,
                type = ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = System.currentTimeMillis(),
            )

        // 1. Setup Content Bank
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = item.videoId,
                title = item.title,
                source = item.source,
                type = item.type,
                currentTimestamp = item.capturedAtTimestamp,
            ),
        )

        // 2. SEED THE POSITION FIRST - This simulates a returning user who already listened to 45 mins
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(podcastUrl, 2700f))

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                                                                if (state.media.activeNativeMediaItem != null) {
                    NativeAudioPlayerModal(
                        contentItem = state.media.activeNativeMediaItem!!,
                        isVisible = true,
                        onMinimize = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseNativePlayer) },
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseNativePlayer) },
                    )
                }
            }
        }

        // 3. Open the player initially. ExoPlayer will immediately error out on the dummy URL.
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
        composeTestRule.waitForIdle()

        // Wait for ExoPlayer to spin up, fail, and set playerError != null in the background.
        Thread.sleep(1500)

        // 4. Close the player, triggering the `onDispose` block.
        GatekeeperStateManager.dispatch(GatekeeperAction.CloseNativePlayer)
        composeTestRule.waitForIdle()

        // 5. Assert: Verify the seeded 45-minute mark was NOT wiped out by ExoPlayer's error state (0).
                val finalState = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(finalState.media.savedMediaPositions[podcastUrl])
            .isEqualTo(2700f)
    }
}
