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
    fun testDeepLink_DispatchesOpenCleanAudioPlayerAction() {
        val testUrl = "https://soundcloud.com/test/track"
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                putExtra("OPEN_CLEAN_AUDIO_URL", testUrl)
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            val currentState = GatekeeperStateManager.state.value
            assertThat(currentState.media.activeAudioUrl).isEqualTo(testUrl)
        }
    }

    @Test
    fun testDeepLink_DispatchesOpenNativePlayerAction() {
        val videoId = "https://example.com/audio.mp3"
        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction.SaveToContentBank(
                videoId = videoId,
                title = "Test Audio",
                source = com.aegisgatekeeper.app.domain.ContentSource.GENERIC,
                type = com.aegisgatekeeper.app.domain.ContentType.AUDIO,
                currentTimestamp = 0L,
            ),
        )

        val savedItem =
            GatekeeperStateManager.state.value.data.contentItems
                .first { it.videoId == videoId }

        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                putExtra("OPEN_NATIVE_AUDIO_ID", savedItem.id)
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            val currentState = GatekeeperStateManager.state.value
            assertThat(currentState.media.activeNativeMediaItem?.id).isEqualTo(savedItem.id)
        }
    }

    @Test
    fun testDeepLink_DispatchesOpenActiveNativePlayerAction() {
        val item =
            com.aegisgatekeeper.app.domain.ContentItem(
                id = "active_item_id",
                videoId = "https://example.com/audio.mp3",
                title = "Test Audio",
                source = com.aegisgatekeeper.app.domain.ContentSource.GENERIC,
                type = com.aegisgatekeeper.app.domain.ContentType.AUDIO,
                rank = 0,
                capturedAtTimestamp = 0L,
            )

        GatekeeperStateManager.dispatch(
            com.aegisgatekeeper.app.domain.GatekeeperAction
                .OpenNativePlayer(item),
        )

        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                putExtra("OPEN_ACTIVE_NATIVE_PLAYER", true)
            }

        ActivityScenario.launch<MainActivity>(intent).use {
            val currentState = GatekeeperStateManager.state.value
            assertThat(currentState.media.activeNativeMediaItem?.id).isEqualTo(item.id)
        }
    }
}
