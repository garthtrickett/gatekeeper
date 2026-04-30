package com.aegisgatekeeper.app.services

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.C
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastMediaServiceTest {

    @Test
    fun testPodcastService_ConfiguresAudioFocusAndSpeechType() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = PodcastMediaService()
        
        // Attach base context to avoid NPEs when Service calls getSystemService or other Context methods during ExoPlayer build
        val attachBaseContextMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContextMethod.isAccessible = true
        attachBaseContextMethod.invoke(service, context)

        // Act
        service.onCreate()

        // Extract the MediaSession via reflection
        val sessionField = PodcastMediaService::class.java.getDeclaredField("mediaSession")
        sessionField.isAccessible = true
        val mediaSession = sessionField.get(service) as MediaSession
        
        val player = mediaSession.player

        // Assert: 
        // If the AudioFocus fix is missing, the content type defaults to C.AUDIO_CONTENT_TYPE_UNKNOWN (0).
        // The fix explicitly sets it to C.AUDIO_CONTENT_TYPE_SPEECH (1) alongside handleAudioFocus = true.
        assertThat(player.audioAttributes.contentType).isEqualTo(C.AUDIO_CONTENT_TYPE_SPEECH)
        assertThat(player.audioAttributes.usage).isEqualTo(C.USAGE_MEDIA)
        
        // Cleanup
        service.onDestroy()
    }
}
