package com.aegisgatekeeper.app.views

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * A lightweight bridge to receive state change events from the YouTube IFrame Player API.
 */
class WebAppInterface(
    private val onVideoEnded: () -> Unit,
    private val onStateChangeCallback: (Int) -> Unit = {},
    private val onTimeUpdateCallback: (Float) -> Unit = {},
) {
    @JavascriptInterface
    fun onTimeUpdate(time: Float) {
        Handler(Looper.getMainLooper()).post {
            onTimeUpdateCallback(time)
        }
    }

    /**
     * This method is called from the JavaScript inside the WebView.
     * @param playerState The state code from the YouTube player. 0 means the video has ended.
     */
    @JavascriptInterface
    fun onStateChange(playerState: Int) {
        // Must run on main thread to update Compose state
        Handler(Looper.getMainLooper()).post {
            if (playerState == 0) {
                onVideoEnded()
            }
            onStateChangeCallback(playerState)
        }
    }

    /**
     * Logs player errors from the WebView to Android's Logcat for better diagnostics.
     * See: https://developers.google.com/youtube/iframe_api_reference#onError
     */
    @JavascriptInterface
    fun logError(errorCode: Int) {
        android.util.Log.e("Gatekeeper.YouTubePlayer", "Received internal player error: $errorCode")
    }
}
