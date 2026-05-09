package com.aegisgatekeeper.app.views

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/** A lightweight bridge to receive state change events from the YouTube IFrame Player API. */
class WebAppInterface(\n    private val onVideoEnded: () -> Unit,\n    private val onStateChangeCallback: (Int) -> Unit = {},\n    private val onTimeUpdateCallback: (Float) -> Unit = {},\n) {\n    @JavascriptInterface\n    fun logMessage(message: String) {\n        android.util.Log.d("Gatekeeper.CleanPlayerJS", message)\n    }\n\n    @JavascriptInterface\n    fun onTimeUpdate(time: String) {\n        val parsedTime = time.toFloatOrNull() ?: 0f\n        Handler(Looper.getMainLooper()).post { onTimeUpdateCallback(parsedTime) }\n    }\n\n    /**\n     * This method is called from the JavaScript inside the WebView.\n     * @param playerState The state code from the YouTube player. 0 means the video has ended.\n     */\n    @JavascriptInterface\n    fun onStateChange(playerState: Int) {\n        // Must run on main thread to update Compose state\n        Handler(Looper.getMainLooper()).post {\n            onStateChangeCallback(playerState)\n            if (playerState == 0) {\n                onVideoEnded()\n            }\n        }\n    }\n\n    /**\n     * Logs player errors from the WebView to Android's Logcat for better diagnostics. See:\n     * https://developers.google.com/youtube/iframe_api_reference#onError\n     */\n    @JavascriptInterface\n    fun logError(errorCode: Int) {\n        android.util.Log.e("Gatekeeper.YouTubePlayer", "Received internal player error: $errorCode")\n    }\n}\n\n
    private val onVideoEnded: () -> Unit,
    private val onStateChangeCallback: (Int) -> Unit = {},
    private val onTimeUpdateCallback: (Float) -> Unit = {},
) {
    @JavascriptInterface
    fun logMessage(message: String) {
        android.util.Log.d("Gatekeeper.CleanPlayerJS", message)
    }

    @JavascriptInterface
    fun onTimeUpdate(time: String) {
        val parsedTime = time.toFloatOrNull() ?: 0f
        Handler(Looper.getMainLooper()).post { onTimeUpdateCallback(parsedTime) }
    }

    /**
     * This method is called from the JavaScript inside the WebView.
     * @param playerState The state code from the YouTube player. 0 means the video has ended.
     */
    @JavascriptInterface
    fun onStateChange(playerState: Int) {
        // Must run on main thread to update Compose state
        Handler(Looper.getMainLooper()).post {
            onStateChangeCallback(playerState)
            if (playerState == 0) {
                onVideoEnded()
            }
        }
    }

    /**
     * Logs player errors from the WebView to Android's Logcat for better diagnostics. See:
     * https://developers.google.com/youtube/iframe_api_reference#onError
     */
    @JavascriptInterface
    fun logError(errorCode: Int) {
        android.util.Log.e("Gatekeeper.YouTubePlayer", "Received internal player error: $errorCode")
    }
}
    private val onVideoEnded: () -> Unit,
    private val onStateChangeCallback: (Int) -> Unit = {},
    private val onTimeUpdateCallback: (Float) -> Unit = {},
) {
    @JavascriptInterface
    fun logMessage(message: String) {
        android.util.Log.d("Gatekeeper.CleanPlayerJS", message)
    }
) {
    @JavascriptInterface
    fun onTimeUpdate(time: String) {
        val parsedTime = time.toFloatOrNull() ?: 0f
        Handler(Looper.getMainLooper()).post { onTimeUpdateCallback(parsedTime) }
    }

    /**
     * This method is called from the JavaScript inside the WebView.
     * @param playerState The state code from the YouTube player. 0 means the video has ended.
     */
    @JavascriptInterface
    fun onStateChange(playerState: Int) {
        // Must run on main thread to update Compose state
        Handler(Looper.getMainLooper()).post {
            onStateChangeCallback(playerState)
            if (playerState == 0) {
                onVideoEnded()
            }
        }
    }

    /**
     * Logs player errors from the WebView to Android's Logcat for better diagnostics. See:
     * https://developers.google.com/youtube/iframe_api_reference#onError
     */
    @JavascriptInterface
    fun logError(errorCode: Int) {
        android.util.Log.e("Gatekeeper.YouTubePlayer", "Received internal player error: $errorCode")
    }
}
