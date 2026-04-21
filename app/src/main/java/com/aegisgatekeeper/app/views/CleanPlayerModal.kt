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
    onClose: () -> Unit,
) {
    var showMetacognition by remember { mutableStateOf(false) }
    val sessionStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentPosition by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    // Intercept the native system back button instead of relying on the Dialog's onDismissRequest
    androidx.activity.compose.BackHandler {
        if (!showMetacognition) {
            showMetacognition = true
        } else {
            val duration = System.currentTimeMillis() - sessionStartTime
            GatekeeperStateManager.dispatch(
                GatekeeperAction.LogSessionMetacognition(
                    packageName = "CleanPlayer: YouTube",
                    durationMillis = duration,
                    emotion = Emotion.SKIPPED,
                    currentTimestamp = System.currentTimeMillis(),
                ),
            )
            onClose()
        }
    }

    val context = LocalContext.current
    val state by GatekeeperStateManager.state.collectAsState()
    val videoTitle =
        remember(videoId) {
            val cleanTitle =
                state.contentItems.find { it.videoId == videoId }?.title
                    ?: state.youtubeSearchResults
                        .find { it.id.videoId == videoId }
                        ?.snippet
                        ?.title
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clean_player_media"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Media Player", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val mediaSession =
            MediaSession(context, "CleanPlayerSession").apply {
                isActive = true
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            webViewRef?.evaluateJavascript("player.playVideo();", null)
                        }

                        override fun onPause() {
                            webViewRef?.evaluateJavascript("player.pauseVideo();", null)
                        }
                    },
                )
            }

        val playFilter = IntentFilter("com.aegisgatekeeper.app.PLAY")
        val pauseFilter = IntentFilter("com.aegisgatekeeper.app.PAUSE")
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    when (intent?.action) {
                        "com.aegisgatekeeper.app.PLAY" -> webViewRef?.evaluateJavascript("player.playVideo();", null)
                        "com.aegisgatekeeper.app.PAUSE" -> webViewRef?.evaluateJavascript("player.pauseVideo();", null)
                    }
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, playFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(receiver, pauseFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, playFilter)
            context.registerReceiver(receiver, pauseFilter)
        }

        playerStateCallback = { playerState ->
            val isPlaying = playerState == 1
            val playbackState = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

            mediaSession.setPlaybackState(
                PlaybackState
                    .Builder()
                    .setState(playbackState, 0L, 1f)
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .build(),
            )

            mediaSession.setMetadata(
                MediaMetadata
                    .Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Aegis Gatekeeper")
                    .build(),
            )

            val playIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("com.aegisgatekeeper.app.PLAY").setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val pauseIntent =
                PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent("com.aegisgatekeeper.app.PAUSE").setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                Notification
                    .Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(videoTitle)
                    .setContentText("Aegis Gatekeeper")
                    .setStyle(
                        Notification
                            .MediaStyle()
                            .setMediaSession(mediaSession.sessionToken)
                            .setShowActionsInCompactView(0),
                    ).setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)

            val pauseIcon =
                android.graphics.drawable.Icon
                    .createWithResource(context, android.R.drawable.ic_media_pause)
            val playIcon =
                android.graphics.drawable.Icon
                    .createWithResource(context, android.R.drawable.ic_media_play)

            if (isPlaying) {
                builder.addAction(Notification.Action.Builder(pauseIcon, "Pause", pauseIntent).build())
            } else {
                builder.addAction(Notification.Action.Builder(playIcon, "Play", playIntent).build())
            }

            notificationManager.notify(1001, builder.build())
        }

        onDispose {
            android.webkit.CookieManager
                .getInstance()
                .flush()
            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(videoId, currentPosition))
            mediaSession.isActive = false
            mediaSession.release()
            notificationManager.cancel(1001)
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                // Absorbs all touch events so they don't fall through to the underlying Main UI
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
        color = Color.Black,
    ) {
        if (showMetacognition) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Was this worth it?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IndustrialButton(onClick = {
                            val duration = System.currentTimeMillis() - sessionStartTime
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.LogSessionMetacognition(
                                    packageName = "CleanPlayer: YouTube",
                                    durationMillis = duration,
                                    emotion = Emotion.HAPPY,
                                    currentTimestamp = System.currentTimeMillis(),
                                ),
                            )
                            onClose()
                        }, text = "Happy")
                        IndustrialButton(onClick = {
                            val duration = System.currentTimeMillis() - sessionStartTime
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.LogSessionMetacognition(
                                    packageName = "CleanPlayer: YouTube",
                                    durationMillis = duration,
                                    emotion = Emotion.ANXIOUS,
                                    currentTimestamp = System.currentTimeMillis(),
                                ),
                            )
                            onClose()
                        }, text = "Anxious")
                        IndustrialButton(onClick = {
                            val duration = System.currentTimeMillis() - sessionStartTime
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.LogSessionMetacognition(
                                    packageName = "CleanPlayer: YouTube",
                                    durationMillis = duration,
                                    emotion = Emotion.DRAINED,
                                    currentTimestamp = System.currentTimeMillis(),
                                ),
                            )
                            onClose()
                        }, text = "Drained")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                IndustrialButton(onClick = {
                    val duration = System.currentTimeMillis() - sessionStartTime
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.LogSessionMetacognition(
                            packageName = "CleanPlayer: YouTube",
                            durationMillis = duration,
                            emotion = Emotion.SKIPPED,
                            currentTimestamp = System.currentTimeMillis(),
                        ),
                    )
                    onClose()
                }, text = "Skip", isWarning = true)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // Header with Close Button
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(8.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    IndustrialButton(onClick = { showMetacognition = true }, text = "Close Video", isWarning = true)
                }

                // The WebView Player injected strictly with an iframe
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
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
                                    onVideoEnded = { showMetacognition = true },
                                    onStateChangeCallback = { state -> playerStateCallback(state) },
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
                                                    'autoplay': 1,
                                                    'controls': 1,
                                                    'rel': 0,
                                                    'showinfo': 0,
                                                    'modestbranding': 1,
                                                    'iv_load_policy': 3,
                                                    'playsinline': 1,
                                                    'origin': 'https://app.aegisgatekeeper.com'
                                                },
                                                events: {
                                                    'onReady': function(e) { e.target.playVideo(); },
                                                    'onStateChange': onPlayerStateChange,
                                                    'onError': onPlayerError
                                                }
                                            });
                                        }

                                        function onPlayerStateChange(event) {
                                            if (typeof Android !== "undefined" && Android !== null) {
                                                Android.onStateChange(event.data);
                                            }
                                        }
                                        setInterval(function() {
                                            if (player && player.getCurrentTime && player.getPlayerState() === 1) {
                                                var time = player.getCurrentTime();
                                                if (typeof Android !== "undefined" && Android !== null) {
                                                    Android.onTimeUpdate(time);
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
                        webView.destroy()
                    },
                )
            }
        }
    }
}
