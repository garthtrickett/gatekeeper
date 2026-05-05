package com.aegisgatekeeper.app.views

import android.webkit.JavascriptInterface
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.parseHumanReadableDuration

/**
 * A simple class that acts as a bridge between the Surgical YouTube WebView's JavaScript
 * and the native Kotlin app's state management. This allows the user to save videos
 * from the web UI directly into the Content Bank.
 */
class YouTubeSurgicalBridge {
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
}
