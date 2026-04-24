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
    fun saveVideo_dispatchesCorrectActionToStateManager() {
        val bridge = YouTubeSurgicalBridge()
        val testId = "dQw4w9WgXcQ"
        val testTitle = "Never Gonna Give You Up"
        val testChannel = "Rick Astley"

        // Act: Simulate JS calling the bridge
        bridge.saveVideo(testId, testTitle, testChannel)

        // Assert: Verify state manager received the item
        val state = GatekeeperStateManager.state.value
        val item = state.contentItems.find { it.videoId == testId }

        assertThat(item).isNotNull()
        assertThat(item?.title).isEqualTo(testTitle)
        assertThat(item?.channelName).isEqualTo(testChannel)
        assertThat(item?.source).isEqualTo(ContentSource.YOUTUBE)
        assertThat(item?.type).isEqualTo(ContentType.VIDEO)
    }
}
