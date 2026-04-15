package com.gatekeeper.app.views

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun CleanPlayerModal(
    videoId: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false), // Allow full screen width
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Close Button
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(8.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close Video", color = Color.White)
                    }
                }

                // The WebView Player injected strictly with an iframe
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false // Allow autoplay
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient() // Required for HTML5 full-screen media

                            val htmlData =
                                """
                                <!DOCTYPE html>
                                <html>
                                <body style="margin:0;padding:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;">
                                    <iframe width="100%" height="100%" 
                                            src="https://www.youtube-nocookie.com/embed/$videoId?rel=0&modestbranding=1&autoplay=1" 
                                            title="YouTube video player" 
                                            frameborder="0" 
                                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                                            allowfullscreen>
                                    </iframe>
                                </body>
                                </html>
                                """.trimIndent()

                            loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlData, "text/html", "UTF-8", null)
                        }
                    },
                )
            }
        }
    }
}
