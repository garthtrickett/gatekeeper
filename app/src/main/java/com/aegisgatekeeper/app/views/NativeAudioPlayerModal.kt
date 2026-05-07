package com.aegisgatekeeper.app.views

import android.content.ComponentName
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.services.PodcastMediaService
import com.google.common.util.concurrent.ListenableFuture
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.delay

@Suppress("FunctionName")
@Composable
fun NativeAudioPlayerModal(
    contentItem: ContentItem,
    isVisible: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var showMetacognition by remember { mutableStateOf(false) }
    val sessionStartTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val state by GatekeeperStateManager.state.collectAsState()
    val savedPosition = state.media.savedMediaPositions[contentItem.videoId] ?: 0f

    val podcastSub = state.sync.podcastSubscriptions.find { it.id == contentItem.podcastId }
    val artworkUrl =
        podcastSub?.artworkUrl
            ?: if (contentItem.source ==
                com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE
            ) {
                "https://img.youtube.com/vi/${contentItem.videoId}/hqdefault.jpg"
            } else {
                null
            }

    DisposableEffect(contentItem.videoId) {
        var controllerFuture: ListenableFuture<MediaController>? = null
        val sessionToken =
            SessionToken(context, ComponentName(context, PodcastMediaService::class.java))

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val mediaController = controllerFuture?.get()
                controller = mediaController
                mediaController?.let { mc ->
                    val isAlreadyPlayingThis =
                        mc.currentMediaItem?.mediaId == contentItem.videoId

                    if (!isAlreadyPlayingThis) {
                        android.util.Log.d(
                            "Gatekeeper",
                            "🎵 NativePlayer: Initializing new media session for ${contentItem.title}",
                        )
                        val uriToPlay =
                            if (contentItem.source ==
                                com.aegisgatekeeper.app.domain.ContentSource
                                    .YOUTUBE ||
                                contentItem.source ==
                                com.aegisgatekeeper.app.domain
                                    .ContentSource.SOUNDCLOUD
                            ) {
                                contentItem.localFilePath // This is the surgical proxy URL
                            } else {
                                contentItem.videoId // For direct audio, always use the
                                // original URL
                            }

                        val mediaItem =
                            MediaItem
                                .Builder()
                                .setMediaId(
                                    contentItem.videoId,
                                ) // Unique ID for session
                                .setUri(uriToPlay) // URL to play
                                .setMediaMetadata(
                                    MediaMetadata
                                        .Builder()
                                        .setTitle(contentItem.title)
                                        .setArtist(
                                            contentItem.channelName
                                                ?: "Podcast",
                                        ).setArtworkUri(
                                            artworkUrl?.let {
                                                android.net.Uri.parse(it)
                                            },
                                        ).build(),
                                ).build()

                        mc.setMediaItem(mediaItem)
                        mc.prepare()
                        mc.seekTo((savedPosition * 1000).toLong())
                        mc.play()
                    } else {
                        android.util.Log.d(
                            "Gatekeeper",
                            "🎵 NativePlayer: Re-attaching to existing background session at ${mc.currentPosition}ms",
                        )
                        // If the service is at 0 but we have a saved position, the service
                        // likely reset.
                        // Resync it without a full media item reset to avoid a 'flicker'.
                        if (mc.currentPosition < 1000 && savedPosition > 2f) {
                            android.util.Log.d(
                                "Gatekeeper",
                                "🎵 NativePlayer: Syncing existing session to saved position: ${savedPosition}s",
                            )
                            mc.seekTo((savedPosition * 1000).toLong())
                        }
                        if (mc.playbackState == Player.STATE_IDLE ||
                            mc.playbackState == Player.STATE_ENDED ||
                            mc.playerError != null
                        ) {
                            mc.prepare()
                        }
                        mc.play()
                    }

                    isPlaying = mc.isPlaying
                    isBuffering =
                        mc.playbackState == Player.STATE_BUFFERING ||
                        mc.playbackState == Player.STATE_IDLE

                    mc.addListener(
                        object : Player.Listener {
                            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                                isPlaying = isPlayingState
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                isBuffering =
                                    playbackState == Player.STATE_BUFFERING ||
                                    playbackState == Player.STATE_IDLE
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.e(
                                    "Gatekeeper",
                                    "🚨 NativePlayer Error: Code ${error.errorCode} - ${error.errorCodeName}",
                                )
                                android.util.Log.e(
                                    "Gatekeeper",
                                    "🚨 NativePlayer Error Message: ${error.message}",
                                    error,
                                )
                                val cause = error.cause
                                if (cause is
                                        androidx.media3.datasource.HttpDataSource.HttpDataSourceException
                                ) {
                                    android.util.Log.e(
                                        "Gatekeeper",
                                        "🚨 NativePlayer HTTP Error: ${cause.message}",
                                    )
                                    android.util.Log.e(
                                        "Gatekeeper",
                                        "🚨 NativePlayer HTTP DataSpec: ${cause.dataSpec.uri}",
                                    )
                                }
                            }
                        },
                    )
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            controller?.let {
                val isValidState = it.duration > 0L || it.currentPosition > 0L
                if (it.playerError == null && isValidState) {
                    val posToSave =
                        if (it.playbackState == Player.STATE_ENDED) {
                            0f
                        } else {
                            it.currentPosition / 1000f
                        }
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.SaveMediaPosition(contentItem.videoId, posToSave),
                    )
                }
                it.pause()
                it.release()
            }
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    LaunchedEffect(controller, isPlaying) {
        while (true) {
            controller?.let {
                currentPosition = it.currentPosition
                duration = it.duration.coerceAtLeast(0L)
            }
            delay(1000L)
        }
    }

    androidx.activity.compose.BackHandler(enabled = isVisible) {
        controller?.let {
            val isValidState = it.duration > 0L || it.currentPosition > 0L
            if (it.playerError == null && isValidState) {
                val posToSave =
                    if (it.playbackState == Player.STATE_ENDED) {
                        0f
                    } else {
                        it.currentPosition / 1000f
                    }
                GatekeeperStateManager.dispatch(
                    GatekeeperAction.SaveMediaPosition(contentItem.videoId, posToSave),
                )
            }
        }
        onMinimize()
    }

    Box(
        modifier =
            if (isVisible) {
                Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {}
            } else {
                Modifier.size(1.dp).alpha(0.01f)
            },
    ) {
        if (isVisible) {
            Surface(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                color = Color.Black,
            ) {
                if (showMetacognition) {
                    Column(
                        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Was this worth it?",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IndustrialButton(
                                onClick = {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.LogSessionMetacognition(
                                            "NativeAudio: Podcast",
                                            System.currentTimeMillis() -
                                                sessionStartTime,
                                            Emotion.HAPPY,
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                    onClose()
                                },
                                text = "Happy",
                            )
                            IndustrialButton(
                                onClick = {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.LogSessionMetacognition(
                                            "NativeAudio: Podcast",
                                            System.currentTimeMillis() -
                                                sessionStartTime,
                                            Emotion.ANXIOUS,
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                    onClose()
                                },
                                text = "Anxious",
                            )
                            IndustrialButton(
                                onClick = {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.LogSessionMetacognition(
                                            "NativeAudio: Podcast",
                                            System.currentTimeMillis() -
                                                sessionStartTime,
                                            Emotion.DRAINED,
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                    onClose()
                                },
                                text = "Drained",
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        IndustrialButton(
                            onClick = {
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.LogSessionMetacognition(
                                        "NativeAudio: Podcast",
                                        System.currentTimeMillis() - sessionStartTime,
                                        Emotion.SKIPPED,
                                        System.currentTimeMillis(),
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IndustrialButton(
                                    onClick = {
                                        controller?.let {
                                            val isValidState =
                                                it.duration > 0L || it.currentPosition > 0L
                                            if (it.playerError == null && isValidState) {
                                                val posToSave =
                                                    if (it.playbackState ==
                                                        Player.STATE_ENDED
                                                    ) {
                                                        0f
                                                    } else {
                                                        it.currentPosition / 1000f
                                                    }
                                                GatekeeperStateManager.dispatch(
                                                    GatekeeperAction.SaveMediaPosition(
                                                        contentItem.videoId,
                                                        posToSave,
                                                    ),
                                                )
                                            }
                                        }
                                        onMinimize()
                                    },
                                    text = "Minimize",
                                )
                                IndustrialButton(
                                    onClick = { showMetacognition = true },
                                    text = "End Session",
                                    isWarning = true,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (artworkUrl != null) {
                                Card(
                                    modifier = Modifier.size(240.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    KamelImage(
                                        resource = asyncPainterResource(data = artworkUrl),
                                        contentDescription = "Podcast Artwork",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            } else {
                                // Placeholder for YouTube videos
                                Box(
                                    modifier =
                                        Modifier
                                            .size(240.dp)
                                            .background(
                                                Color.Black,
                                                shape = MaterialTheme.shapes.medium,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) { Text("🎬", fontSize = 120.sp) }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    contentItem.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                                if (contentItem.downloadStatus ==
                                    com.aegisgatekeeper.app.domain.DownloadStatus
                                        .COMPLETED
                                ) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = {
                                            Text(
                                                "OFFLINE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        },
                                        colors =
                                            androidx.compose.material3.FilterChipDefaults
                                                .filterChipColors(
                                                    selectedContainerColor =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary,
                                                    selectedLabelColor =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onPrimary,
                                                ),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                contentItem.channelName ?: "Podcast",
                                color = Color.Gray,
                                fontSize = 16.sp,
                                maxLines = 1,
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Slider(
                                value =
                                    if (duration > 0) {
                                        currentPosition.toFloat() / duration
                                    } else {
                                        0f
                                    },
                                onValueChange = {
                                    controller?.seekTo((it * duration).toLong())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor =
                                            MaterialTheme.colorScheme.primary,
                                    ),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    formatTime(currentPosition),
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                )
                                Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                IndustrialButton(
                                    onClick = {
                                        val speeds = listOf(1.0f, 1.2f, 1.5f, 2.0f)
                                        val nextIndex =
                                            (speeds.indexOf(playbackSpeed) + 1) %
                                                speeds.size
                                        playbackSpeed = speeds[nextIndex]
                                        controller?.setPlaybackSpeed(playbackSpeed)
                                    },
                                    text = "${playbackSpeed}x",
                                )

                                IndustrialButton(
                                    onClick = { controller?.seekTo(currentPosition - 15000) },
                                    text = "-15s",
                                )

                                IndustrialButton(
                                    onClick = {
                                        if (isPlaying) {
                                            controller?.pause()
                                        } else {
                                            controller?.play()
                                        }
                                    },
                                    text = if (isPlaying) "Pause" else "Play",
                                    isWarning = isPlaying,
                                    isLoading = isBuffering,
                                )

                                IndustrialButton(
                                    onClick = { controller?.seekTo(currentPosition + 30000) },
                                    text = "+30s",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
