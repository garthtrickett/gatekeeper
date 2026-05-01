// app/src/main/java/com/aegisgatekeeper/app/views/CleanPlayerModal.kt

package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
actual fun CleanPlayerModal(
    videoId: String,
    isVisible: Boolean,
    onMinimize: () -> Unit,
    onStop: () -> Unit,
) {
    val sessionStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentPosition by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    // Intercept the native system back button instead of relying on the Dialog's onDismissRequest
    androidx.activity.compose.BackHandler(enabled = isVisible) {
        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, currentPosition))
        onMinimize()
    }

    val context = LocalContext.current
    val state by GatekeeperStateManager.state.collectAsState()
    val videoTitle =
        remember(videoId) {
            val cleanTitle =
                state.contentItems.find { it.videoId == videoId }?.title
                    ?: "Clean Player Video"
            cleanTitle
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var playerStateCallback by remember { mutableStateOf<(Int) -> Unit>({}) }

    val startSeconds = state.savedMediaPositions[videoId] ?: 0f

    DisposableEffect(videoId) {
        val startIntent =
            Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java).apply {
                action = "com.aegisgatekeeper.app.SERVICE_START"
                putExtra("EXTRA_TITLE", videoTitle)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }

        val playFilter = IntentFilter("com.aegisgatekeeper.app.WEB_PLAY")
        val pauseFilter = IntentFilter("com.aegisgatekeeper.app.WEB_PAUSE")
        val stopFilter = IntentFilter("com.aegisgatekeeper.app.WEB_STOP")
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    when (intent?.action) {
                        "com.aegisgatekeeper.app.WEB_PLAY" -> {
                            webViewRef?.evaluateJavascript("player.playVideo();", null)
                        }

                        "com.aegisgatekeeper.app.WEB_PAUSE" -> {
                            webViewRef?.evaluateJavascript("player.pauseVideo();", null)
                        }

                        "com.aegisgatekeeper.app.WEB_STOP" -> {
                            val duration = System.currentTimeMillis() - sessionStartTime
                            GatekeeperStateManager.dispatch(GatekeeperAction.TriggerMetacognition("CleanPlayer: YouTube", duration))
                            onStop()
                        }
                    }
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, playFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(receiver, pauseFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(receiver, stopFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, playFilter)
            context.registerReceiver(receiver, pauseFilter)
            context.registerReceiver(receiver, stopFilter)
        }

        playerStateCallback = { playerState ->
            val isPlaying = playerState == 1
            val updateIntent =
                Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java).apply {
                    action = "com.aegisgatekeeper.app.SERVICE_UPDATE"
                    putExtra("EXTRA_IS_PLAYING", isPlaying)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(updateIntent)
            } else {
                context.startService(updateIntent)
            }
        }

        onDispose {
            android.webkit.CookieManager
                .getInstance()
                .flush()
            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, currentPosition))

            val stopIntent = Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java)
            context.stopService(stopIntent)

            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(
        modifier =
            if (isVisible) {
                Modifier
                    .fillMaxSize()
                    // Absorbs all touch events so they don't fall through to the underlying Main UI
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {}
            } else {
                Modifier
                    .size(1.dp)
                    .alpha(0.01f)
            },
    ) {
        Column(modifier = if (isVisible) Modifier.fillMaxSize().systemBarsPadding() else Modifier.fillMaxSize()) {
            // Header with Buttons
            if (isVisible) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray)
                            .padding(8.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IndustrialButton(onClick = {
                            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, currentPosition))
                            onMinimize()
                        }, text = "Minimize")
                        IndustrialButton(onClick = {
                            val duration = System.currentTimeMillis() - sessionStartTime
                            GatekeeperStateManager.dispatch(GatekeeperAction.TriggerMetacognition("CleanPlayer: YouTube", duration))
                            onStop()
                        }, text = "End Session", isWarning = true)
                    }
                }
            }

                        // The WebView Player injected strictly with an iframe
            androidx.compose.runtime.key(videoId) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                    object : WebView(context) {
                        override fun onWindowVisibilityChanged(visibility: Int) {
                            super.onWindowVisibilityChanged(android.view.View.VISIBLE)
                        }

                        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                            super.onWindowFocusChanged(true)
                        }

                        override fun onVisibilityChanged(
                            changedView: android.view.View,
                            visibility: Int,
                        ) {
                            super.onVisibilityChanged(changedView, android.view.View.VISIBLE)
                        }
                    }.apply {
                        // Enforce match parent so it correctly sizes without the Dialog wrapper interference
                        layoutParams =
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false // Allow autoplay
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")

                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = WebChromeClient() // Required for HTML5 full-screen media

                        // Harden the WebView against external navigation
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                ): Boolean {
                                    val urlStr = request?.url?.toString() ?: ""
                                    if (urlStr.startsWith("intent://") || urlStr.startsWith("vnd.youtube:")) {
                                        return true
                                    }
                                    val isAuthFlow =
                                        urlStr.contains("accounts.google.com") ||
                                            urlStr.contains("myaccount.google.com") ||
                                            urlStr.contains("accounts.youtube.com")
                                    if (isAuthFlow) {
                                        return false
                                    }
                                    // Allow iframe API sub-resource loads, but block main frame navigations
                                    // to prevent the user from escaping into standard YouTube.
                                    return request?.isForMainFrame == true
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    super.onPageFinished(view, url)
                                    // Inject CSS to hide the "Watch on YouTube" logo and other junk
                                    val css =
                                        ".ytp-impression-link, .ytp-watermark, " +
                                            ".ytp-watch-later-button { display: none !important; }"
                                    val js =
                                        "var style = document.createElement('style'); " +
                                            "style.innerHTML = '$css'; " +
                                            "document.head.appendChild(style);"
                                    view?.evaluateJavascript(js, null)
                                }
                            }

                        webViewRef = this
                        addJavascriptInterface(
                            WebAppInterface(
                                onVideoEnded = {
                                    val duration = System.currentTimeMillis() - sessionStartTime
                                    GatekeeperStateManager.dispatch(GatekeeperAction.TriggerMetacognition("CleanPlayer: YouTube", duration))
                                    onStop()
                                },
                                onStateChangeCallback = { state ->
                                    playerStateCallback(state)
                                    if (state == 0) { // ENDED
                                        currentPosition = 0f
                                        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, 0f))
                                    } else if (state == 2) { // PAUSED
                                        GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, currentPosition))
                                    }
                                },
                                onTimeUpdateCallback = { time -> currentPosition = time },
                            ),
                            "Android",
                        )

                        val htmlData =
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <style>
                                    body, html { margin:0; padding:0; height:100%; overflow:hidden; background-color:#000; }
                                    #player { height:100%; width:100%; }
                                </style>
                            </head>
                            <body>
                                <div id="player"></div>
                                <script>
                                    var tag = document.createElement('script');
                                    tag.src = "https://www.youtube.com/iframe_api";
                                    var firstScriptTag = document.getElementsByTagName('script')[0];
                                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                                    var player;
                                    function onYouTubeIframeAPIReady() {
                                        player = new YT.Player('player', {
                                            height: '100%',
                                            width: '100%',
                                            videoId: '$videoId',
                                                                                        playerVars: {
                                                'start': ${startSeconds.toInt()},
                                                'autoplay': ${if (isVisible) 1 else 0},
                                                'controls': 1,
                                                'rel': 0,
                                                'showinfo': 0,
                                                'modestbranding': 1,
                                                'iv_load_policy': 3,
                                                'playsinline': 1,
                                                'origin': 'https://app.aegisgatekeeper.com'
                                            },
                                                                                        events: {
                                                'onReady': function(e) {
                                                    if (${if (isVisible) "true" else "false"}) {
                                                        e.target.playVideo();
                                                    }
                                                },
                                                'onStateChange': onPlayerStateChange,
                                                'onError': onPlayerError
                                            }
                                        });
                                    }

                                    var lastState = -1;
                                    function onPlayerStateChange(event) {
                                        if (typeof Android !== "undefined" && Android !== null) {
                                            if (player && player.getCurrentTime) {
                                                Android.onTimeUpdate(player.getCurrentTime().toString());
                                            }
                                            lastState = event.data;
                                            Android.onStateChange(event.data);
                                        }
                                    }
                                    setInterval(function() {
                                        if (player && player.getCurrentTime) {
                                            var state = player.getPlayerState();
                                            var time = player.getCurrentTime();
                                            if (typeof Android !== "undefined" && Android !== null) {
                                                Android.onTimeUpdate(time.toString());
                                            }
                                            if (state !== lastState && (state === 1 || state === 2 || state === 0)) {
                                                lastState = state;
                                                if (typeof Android !== "undefined" && Android !== null) {
                                                    Android.onStateChange(state);
                                                }
                                            }
                                        }
                                    }, 1000);

                                    function onPlayerError(event) {
                                        // 2: invalid parameter. 5: HTML5 player error.
                                        // 100: video not found. 101/150: not embeddable.
                                        console.error('YouTube Player Error: ' + event.data);
                                        if (typeof Android !== "undefined" && Android !== null) {
                                            Android.logError(event.data);
                                        }
                                    }
                                </script>
                            </html>
                            """.trimIndent()

                        loadDataWithBaseURL("https://app.aegisgatekeeper.com/", htmlData, "text/html", "UTF-8", null)
                    }
                },
                                onRelease = { webView ->
                    webViewRef = null
                    webView.destroy()
                },
            )
            }
        }
    }
}
