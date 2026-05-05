package com.aegisgatekeeper.app.views

import android.webkit.JavascriptInterface

class WebAppInterface(
    private val onVideoEnded: () -> Unit,
    private val onStateChangeCallback: (Int) -> Unit,
    private val onTimeUpdateCallback: (Float) -> Unit,
) {
    @JavascriptInterface
    fun onStateChange(state: Int) {
        onStateChangeCallback(state)
    }

    @JavascriptInterface
    fun onTimeUpdate(time: String) {
        onTimeUpdateCallback(time.toFloatOrNull() ?: 0f)
    }

    @JavascriptInterface
    fun onVideoEnded() {
        onVideoEnded()
    }
}