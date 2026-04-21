package com.aegisgatekeeper.app.views

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebAppInterfaceTest {
    @Test
    fun onStateChange_whenStateIs0_triggersCallback() {
        val latch = CountDownLatch(1)
        var callbackTriggered = false

        val webAppInterface =
            WebAppInterface(
                onVideoEnded = {
                    callbackTriggered = true
                    latch.countDown()
                },
            )

        // Act
        webAppInterface.onStateChange(0) // State: ENDED

        // Assert: Wait for the main looper to execute the callback
        val success = latch.await(2, TimeUnit.SECONDS)
        assertThat(success).isTrue()
        assertThat(callbackTriggered).isTrue()
    }

    @Test
    fun onStateChange_whenStateIsNot0_doesNotTriggerCallback() {
        var callbackTriggered = false
        val webAppInterface =
            WebAppInterface(
                onVideoEnded = {
                    callbackTriggered = true
                },
            )

        // Act
        webAppInterface.onStateChange(-1) // UNSTARTED
        webAppInterface.onStateChange(1) // PLAYING
        webAppInterface.onStateChange(2) // PAUSED
        webAppInterface.onStateChange(3) // BUFFERING
        webAppInterface.onStateChange(5) // CUED

        // Assert: Wait a moment to ensure the callback is NOT triggered
        Thread.sleep(500)
        assertThat(callbackTriggered).isFalse()
    }

    @Test
    fun onTimeUpdate_triggersCallback() {
        val latch = CountDownLatch(1)
        var receivedTime = -1f

        val webAppInterface =
            WebAppInterface(
                onVideoEnded = { },
                onStateChangeCallback = { },
                onTimeUpdateCallback = { time ->
                    receivedTime = time
                    latch.countDown()
                },
            )

        // Act
        webAppInterface.onTimeUpdate(15.5f)

        // Assert: Wait for the main looper to execute the callback
        val success = latch.await(2, TimeUnit.SECONDS)
        assertThat(success).isTrue()
        assertThat(receivedTime).isEqualTo(15.5f)
    }
}
