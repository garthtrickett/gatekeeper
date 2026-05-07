package com.aegisgatekeeper.app.views

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.resetStateForTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeSurgicalBridgeTest {
    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    @Test
    fun playVideo_dispatchesOpenCleanPlayerAction() {
        val bridge = YouTubeSurgicalBridge()
        bridge.playVideo("dQw4w9WgXcQ")

        val state = GatekeeperStateManager.state.value
        assertThat(state.media.activeVideoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(state.media.isVideoPlayerMaximized).isTrue()
    }

    @Test
    fun saveVideo_dispatchesCorrectActionToStateManager() {
        val bridge = YouTubeSurgicalBridge()
        val testId = "dQw4w9WgXcQ"
        val testTitle = "Never Gonna Give You Up"
        val testChannel = "Rick Astley"
        val testDuration = "15:33"

        // Act: Simulate JS calling the bridge
        bridge.saveVideo(testId, testTitle, testChannel, testDuration)

        // Assert: Verify state manager received the item
        val state = GatekeeperStateManager.state.value
        val item = state.data.contentItems.find { it.videoId == testId }

        assertThat(item).isNotNull()
        assertThat(item?.title).isEqualTo(testTitle)
        assertThat(item?.channelName).isEqualTo(testChannel)
        assertThat(item?.durationSeconds).isEqualTo(933L) // 15*60 + 33
        assertThat(item?.source).isEqualTo(ContentSource.YOUTUBE)
        assertThat(item?.type).isEqualTo(ContentType.VIDEO)
    }

    @Test
    fun toggleSafeChannel_dispatchesCorrectAction() {
        val bridge = YouTubeSurgicalBridge()
        val channelId = "UC-lHJ-WFAkQKdfcz6L7P6Ig"
        val channelName = "Test Channel"

        bridge.toggleSafeChannel(channelId, channelName)

        val state = GatekeeperStateManager.state.value
        assertThat(state.data.safeYouTubeChannels).containsKey(channelId)
        assertThat(state.data.safeYouTubeChannels[channelId]).isEqualTo(channelName)
    }
}
