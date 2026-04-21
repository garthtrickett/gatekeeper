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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun IntentionalContentScreen() {
    val state by GatekeeperStateManager.state.collectAsState()

    if (!state.isProTier) {
        PaywallScreen(
            title = "Intentional Slots Dashboard",
            description =
                "Upgrade to Pro to unlock the 5-slot constraint dashboard. Pin exactly 5 pieces " +
                    "of content to your Widget and consume them without algorithmic recommendations.",
        )
        return
    }

    var showFrictionForSlot by remember { mutableStateOf<Int?>(null) }
    var slotToEdit by remember { mutableStateOf<Int?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Intentional Slots", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your 5 'SD Card' slots. High friction to change.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            for (i in 0 until 5) {
                val item = state.intentionalSlots.find { it.slotIndex == i }
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item?.contentItem?.title ?: "Empty Slot ${i + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                if (item != null) {
                                    Text(
                                        text = "${item.contentItem.type.name} • ${item.contentItem.source.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item != null) {
                                    IndustrialButton(
                                        onClick = {
                                            when (item.contentItem.type) {
                                                com.aegisgatekeeper.app.domain.ContentType.VIDEO -> {
                                                    GatekeeperStateManager.dispatch(
                                                        GatekeeperAction.OpenCleanPlayer(item.contentItem.videoId),
                                                    )
                                                }

                                                com.aegisgatekeeper.app.domain.ContentType.AUDIO -> {
                                                    GatekeeperStateManager.dispatch(
                                                        GatekeeperAction.OpenCleanAudioPlayer(item.contentItem.videoId),
                                                    )
                                                }

                                                com.aegisgatekeeper.app.domain.ContentType.READING -> {
                                                    val intent =
                                                        android.content.Intent(
                                                            android.content.Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(item.contentItem.videoId),
                                                        )
                                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                    com.aegisgatekeeper.app.App.instance
                                                        .startActivity(intent)
                                                }
                                            }
                                        },
                                        text =
                                            if (item.contentItem.type ==
                                                com.aegisgatekeeper.app.domain.ContentType.READING
                                            ) {
                                                "Read"
                                            } else {
                                                "Play"
                                            },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                IndustrialButton(
                                    onClick = {
                                        showFrictionForSlot = i
                                    },
                                    text = if (item != null) "Eject" else "Insert",
                                    isWarning = item != null,
                                )
                            }
                        }

                        if (item != null) {
                            val savedPosition = state.savedMediaPositions[item.contentItem.videoId]
                            if (savedPosition != null && savedPosition > 0f && item.contentItem.durationSeconds != null &&
                                item.contentItem.durationSeconds > 0
                            ) {
                                val progress = (savedPosition / item.contentItem.durationSeconds).coerceIn(0f, 1f)
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showFrictionForSlot != null) {
            Dialog(
                onDismissRequest = { showFrictionForSlot = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BallBalancingUi(
                    title = "Unlock Slot",
                    subtitle = "Complete this task to modify Slot ${showFrictionForSlot!! + 1}.",
                    onSuccess = {
                        val slot = showFrictionForSlot!!
                        showFrictionForSlot = null
                        slotToEdit = slot
                    },
                    onClose = { showFrictionForSlot = null },
                )
            }
        }

        if (slotToEdit != null) {
            Dialog(onDismissRequest = { slotToEdit = null }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().height(460.dp).padding(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Assign Content to Slot ${slotToEdit!! + 1}", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        val availableItems =
                            state.contentItems.filter { content ->
                                state.intentionalSlots.none { it.contentItem.id == content.id }
                            }

                        if (availableItems.isEmpty()) {
                            Text(
                                "No available items in the Content Bank. Go save something first!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(availableItems) { content ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        onClick = {
                                            GatekeeperStateManager.dispatch(GatekeeperAction.SaveIntentionalSlot(slotToEdit!!, content))
                                            slotToEdit = null
                                        },
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(content.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                            Text(
                                                "${content.type.name} • ${content.source.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            val currentItem = state.intentionalSlots.find { it.slotIndex == slotToEdit }
                            if (currentItem != null) {
                                IndustrialButton(
                                    onClick = {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.ClearIntentionalSlot(slotToEdit!!))
                                        slotToEdit = null
                                    },
                                    text = "Clear Slot",
                                    isWarning = true,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IndustrialButton(onClick = { slotToEdit = null }, text = "Cancel", isWarning = true)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CleanAudioPlayerModal(
    url: String,
    onClose: () -> Unit,
) {
    var showMetacognition by remember { mutableStateOf(false) }
    val sessionStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val state by GatekeeperStateManager.state.collectAsState()
    val audioTitle =
        remember(url) {
            val cleanTitle = state.contentItems.find { it.videoId == url }?.title ?: "Clean Audio Player"
            cleanTitle
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var playerStateCallback by remember { mutableStateOf<(Int) -> Unit>({}) }

    val startSeconds = state.savedMediaPositions[url] ?: 0f

    DisposableEffect(url) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clean_audio_media"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Audio Player", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val mediaSession =
            MediaSession(context, "CleanAudioSession").apply {
                isActive = true
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            webViewRef?.evaluateJavascript("widget.play();", null)
                        }

                        override fun onPause() {
                            webViewRef?.evaluateJavascript("widget.pause();", null)
                        }
                    },
                )
            }

        val playFilter = IntentFilter("com.aegisgatekeeper.app.AUDIO_PLAY")
        val pauseFilter = IntentFilter("com.aegisgatekeeper.app.AUDIO_PAUSE")
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    when (intent?.action) {
                        "com.aegisgatekeeper.app.AUDIO_PLAY" -> webViewRef?.evaluateJavascript("widget.play();", null)
                        "com.aegisgatekeeper.app.AUDIO_PAUSE" -> webViewRef?.evaluateJavascript("widget.pause();", null)
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
                    .putString(MediaMetadata.METADATA_KEY_TITLE, audioTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Aegis Gatekeeper")
                    .build(),
            )

            val playIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("com.aegisgatekeeper.app.AUDIO_PLAY").setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val pauseIntent =
                PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent("com.aegisgatekeeper.app.AUDIO_PAUSE").setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                Notification
                    .Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(audioTitle)
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

            notificationManager.notify(1002, builder.build())
        }

        onDispose {
            android.webkit.CookieManager
                .getInstance()
                .flush()
            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(url, currentPosition))
            mediaSession.isActive = false
            mediaSession.release()
            notificationManager.cancel(1002)
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(url) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var currentUrl = url
                var redirects = 0
                while (currentUrl.contains("on.soundcloud.com") && redirects < 5) {
                    val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                    conn.instanceFollowRedirects = false
                    if (conn.responseCode in 300..399) {
                        currentUrl = conn.getHeaderField("Location") ?: currentUrl
                    }
                    redirects++
                }
                // Strip query params if any
                if (currentUrl.contains("?")) {
                    currentUrl = currentUrl.substringBefore("?")
                }
                resolvedUrl = currentUrl
            } catch (e: Exception) {
                resolvedUrl = url
            }
        }
    }

    androidx.activity.compose.BackHandler {
        if (!showMetacognition) {
            showMetacognition = true
        } else {
            val duration = System.currentTimeMillis() - sessionStartTime
            GatekeeperStateManager.dispatch(
                GatekeeperAction.LogSessionMetacognition(
                    packageName = "CleanAudio: Player",
                    durationMillis = duration,
                    emotion = Emotion.SKIPPED,
                    currentTimestamp = System.currentTimeMillis(),
                ),
            )
            onClose()
        }
    }

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
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
                        IndustrialButton(
                            onClick = {
                                val duration = System.currentTimeMillis() - sessionStartTime
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.LogSessionMetacognition(
                                        packageName = "CleanAudio: Player",
                                        durationMillis = duration,
                                        emotion = Emotion.HAPPY,
                                        currentTimestamp = System.currentTimeMillis(),
                                    ),
                                )
                                onClose()
                            },
                            text = "Happy",
                        )
                        IndustrialButton(
                            onClick = {
                                val duration = System.currentTimeMillis() - sessionStartTime
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.LogSessionMetacognition(
                                        packageName = "CleanAudio: Player",
                                        durationMillis = duration,
                                        emotion = Emotion.ANXIOUS,
                                        currentTimestamp = System.currentTimeMillis(),
                                    ),
                                )
                                onClose()
                            },
                            text = "Anxious",
                        )
                        IndustrialButton(
                            onClick = {
                                val duration = System.currentTimeMillis() - sessionStartTime
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.LogSessionMetacognition(
                                        packageName = "CleanAudio: Player",
                                        durationMillis = duration,
                                        emotion = Emotion.DRAINED,
                                        currentTimestamp = System.currentTimeMillis(),
                                    ),
                                )
                                onClose()
                            },
                            text = "Drained",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                IndustrialButton(
                    onClick = {
                        val duration = System.currentTimeMillis() - sessionStartTime
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.LogSessionMetacognition(
                                packageName = "CleanAudio: Player",
                                durationMillis = duration,
                                emotion = Emotion.SKIPPED,
                                currentTimestamp = System.currentTimeMillis(),
                            ),
                        )
                        onClose()
                    },
                    text = "Skip",
                    isWarning = true,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray)
                            .padding(8.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    IndustrialButton(onClick = { showMetacognition = true }, text = "Close Audio", isWarning = true)
                }

                if (resolvedUrl == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams =
                                    android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                settings.javaScriptEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.domStorageEnabled = true
                                settings.userAgentString = settings.userAgentString.replace("; wv", "")

                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: android.webkit.WebResourceRequest?,
                                        ): Boolean {
                                            val urlStr = request?.url?.toString() ?: ""
                                            if (urlStr.startsWith("intent://") || urlStr.startsWith("soundcloud://")) {
                                                return true
                                            }
                                            val isAuthFlow =
                                                urlStr.contains("accounts.google.com") ||
                                                    urlStr.contains("myaccount.google.com") ||
                                                    urlStr.contains("accounts.youtube.com")
                                            if (isAuthFlow) {
                                                return false
                                            }
                                            // Block navigation to avoid escaping the clean room iframe
                                            return request?.isForMainFrame == true
                                        }
                                    }
                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                            android.util.Log.d(
                                                "Gatekeeper",
                                                "🎵 CleanAudioPlayer WebView Console: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}",
                                            )
                                            return super.onConsoleMessage(consoleMessage)
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

                                val finalUrl = resolvedUrl!!
                                android.util.Log.d("Gatekeeper", "🎵 CleanAudioPlayerModal: Final Resolved URL -> $finalUrl")
                                val encodedUrl = java.net.URLEncoder.encode(finalUrl, "UTF-8")
                                val htmlData =
                                    """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1">
                                        <style>
                                            body, html { margin:0; padding:0; height:100%; overflow:hidden; background-color:#000; }
                                            iframe { width:100%; height:100%; border:none; }
                                        </style>
                                    </head>
                                    <body>
                                        <iframe id="sc-widget" src="https://w.soundcloud.com/player/?url=$encodedUrl&color=%23ff5500&auto_play=true&hide_related=true&show_comments=false&show_user=false&show_reposts=false&show_teaser=false&visual=true" allow="autoplay"></iframe>
                                        <script src="https://w.soundcloud.com/player/api.js" type="text/javascript"></script>
                                        <script>
                                            var widgetIframe = document.getElementById('sc-widget');
                                            var widget = SC.Widget(widgetIframe);

                                            widget.bind(SC.Widget.Events.READY, function() {
                                                widget.seekTo(${startSeconds.toInt() * 1000});
                                                widget.play();
                                            });

                                            setInterval(function() {
                                                widget.getPosition(function(position) {
                                                    if (typeof Android !== "undefined" && Android !== null) {
                                                        Android.onTimeUpdate(position / 1000.0);
                                                    }
                                                });
                                            }, 1000);

                                            widget.bind(SC.Widget.Events.FINISH, function() {
                                                if (typeof Android !== "undefined" && Android !== null) {
                                                    Android.onStateChange(0);
                                                }
                                            });

                                            widget.bind(SC.Widget.Events.PLAY, function() {
                                                if (typeof Android !== "undefined" && Android !== null) {
                                                    Android.onStateChange(1);
                                                }
                                            });

                                            widget.bind(SC.Widget.Events.PAUSE, function() {
                                                if (typeof Android !== "undefined" && Android !== null) {
                                                    Android.onStateChange(2);
                                                }
                                            });
                                        </script>
                                    </body>
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
}
