package com.gatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareRegexTest {
    // We copy the exact regex used in the GatekeeperStateManager here to verify it parses URLs correctly.
    private val youtubePattern = """(?<=youtu\.be/|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()

    @Test
    fun testExtractsFromStandardWatchUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=4s"
        val videoId = youtubePattern.find(url)?.value
        assertThat(videoId).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun testExtractsFromShortenedYoutuBeUrl() {
        val url = "https://youtu.be/J---aiyznGQ?si=xyz123"
        val videoId = youtubePattern.find(url)?.value
        assertThat(videoId).isEqualTo("J---aiyznGQ")
    }

    @Test
    fun testExtractsFromShortsUrl() {
        val url = "https://www.youtube.com/shorts/1x2y3z4A5B6"
        val videoId = youtubePattern.find(url)?.value
        assertThat(videoId).isEqualTo("1x2y3z4A5B6")
    }

    @Test
    fun testExtractsFromMessyAndroidShareText() {
        // Android often prefixes the URL with text
        val text = "Check out this video! https://youtu.be/abc123DEF45 It's awesome."
        val videoId = youtubePattern.find(text)?.value
        assertThat(videoId).isEqualTo("abc123DEF45")
    }

    @Test
    fun testReturnsNullForGenericLinks() {
        val url = "https://substack.com/post/12345"
        val videoId = youtubePattern.find(url)?.value
        assertThat(videoId).isNull()
    }
}
