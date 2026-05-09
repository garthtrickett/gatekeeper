package com.aegisgatekeeper.app.views

import android.webkit.JavascriptInterface
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.parseHumanReadableDuration

/**
 * A simple class that acts as a bridge between the Surgical YouTube WebView's JavaScript and the
 * native Kotlin app's state management.
 */
class YouTubeSurgicalBridge {
    @JavascriptInterface
    fun logMessage(message: String) {
        android.util.Log.d("Gatekeeper", "💬 JS: $message")
    }

    @JavascriptInterface
    fun playVideo(videoId: String) {
        GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanPlayer(videoId))
    }

    @JavascriptInterface
    fun saveVideo(
        videoId: String,
        title: String,
        channelName: String,
        duration: String,
    ) {
        val durationSeconds = parseHumanReadableDuration(duration)
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = videoId,
                title = title,
                channelName = channelName,
                durationSeconds = durationSeconds,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = System.currentTimeMillis(),
            ),
        )
    }

    @JavascriptInterface
    fun toggleSafeChannel(
        channelId: String,
        channelName: String,
    ) {
        GatekeeperStateManager.dispatch(
            GatekeeperAction.ToggleSafeYouTubeChannel(channelId, channelName),
        )
    }

    @JavascriptInterface
    fun logHtml(html: String) {
        // Log the first 4000 characters for debugging selectors
        android.util.Log.d("Gatekeeper.HTML", html.take(4000))
    }
}
