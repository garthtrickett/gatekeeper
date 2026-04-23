package com.aegisgatekeeper.app.views

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showMetacognition by remember { mutableStateOf(false) }
    val sessionStartTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val state by GatekeeperStateManager.state.collectAsState()
    val savedPosition = state.savedMediaPositions[contentItem.videoId] ?: 0f

    val podcastSub = state.podcastSubscriptions.find { it.id == contentItem.podcastId }
    val artworkUrl = podcastSub?.artworkUrl

    DisposableEffect(contentItem.videoId) {
        var controllerFuture: ListenableFuture<MediaController>? = null
        val sessionToken = SessionToken(context, ComponentName(context, PodcastMediaService::class.java))

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val mediaController = controllerFuture?.get()
                controller = mediaController
                mediaController?.let { mc ->
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(contentItem.videoId)
                        .setUri(contentItem.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(contentItem.title)
                                .setArtist(contentItem.channelName ?: "Podcast")
                                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                                .build()
                        )
                        .build()

                    mc.setMediaItem(mediaItem)
                    mc.prepare()
                    mc.seekTo((savedPosition * 1000).toLong())
                    mc.play()

                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlayingState: Boolean) {
                            isPlaying = isPlayingState
                        }
                    })
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            controller?.let {
                GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(contentItem.videoId, it.currentPosition / 1000f))
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

    androidx.activity.compose.BackHandler {
        if (!showMetacognition) {
            showMetacognition = true
        } else {
            val sessionDuration = System.currentTimeMillis() - sessionStartTime
            GatekeeperStateManager.dispatch(
                GatekeeperAction.LogSessionMetacognition(
                    packageName = "NativeAudio: Podcast",
                    durationMillis = sessionDuration,
                    emotion = Emotion.SKIPPED,
                    currentTimestamp = System.currentTimeMillis(),
                )
            )
            onClose()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .background(Color.Black),
        color = Color.Black
    ) {
        if (showMetacognition) {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Was this worth it?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.LogSessionMetacognition("NativeAudio: Podcast", System.currentTimeMillis() - sessionStartTime, Emotion.HAPPY, System.currentTimeMillis()))
                        onClose()
                    }, text = "Happy")
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.LogSessionMetacognition("NativeAudio: Podcast", System.currentTimeMillis() - sessionStartTime, Emotion.ANXIOUS, System.currentTimeMillis()))
                        onClose()
                    }, text = "Anxious")
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.LogSessionMetacognition("NativeAudio: Podcast", System.currentTimeMillis() - sessionStartTime, Emotion.DRAINED, System.currentTimeMillis()))
                        onClose()
                    }, text = "Drained")
                }
                Spacer(modifier = Modifier.height(24.dp))
                IndustrialButton(onClick = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.LogSessionMetacognition("NativeAudio: Podcast", System.currentTimeMillis() - sessionStartTime, Emotion.SKIPPED, System.currentTimeMillis()))
                    onClose()
                }, text = "Skip", isWarning = true)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color.DarkGray).padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IndustrialButton(onClick = { showMetacognition = true }, text = "Close", isWarning = true)
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    artworkUrl?.let { url ->
                        Card(modifier = Modifier.size(240.dp), shape = MaterialTheme.shapes.medium) {
                            KamelImage(
                                resource = asyncPainterResource(data = url),
                                contentDescription = "Podcast Artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    Text(contentItem.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(contentItem.channelName ?: "Podcast", color = Color.Gray, fontSize = 16.sp, maxLines = 1)

                    Spacer(modifier = Modifier.height(32.dp))

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { controller?.seekTo((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPosition), color = Color.Gray, fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IndustrialButton(onClick = {
                            val speeds = listOf(1.0f, 1.2f, 1.5f, 2.0f)
                            val nextIndex = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                            playbackSpeed = speeds[nextIndex]
                            controller?.setPlaybackSpeed(playbackSpeed)
                        }, text = "${playbackSpeed}x")

                        IndustrialButton(onClick = { controller?.seekTo(currentPosition - 15000) }, text = "-15s")

                        IndustrialButton(
                            onClick = {
                                if (isPlaying) controller?.pause() else controller?.play()
                            },
                            text = if (isPlaying) "Pause" else "Play",
                            isWarning = isPlaying
                        )

                        IndustrialButton(onClick = { controller?.seekTo(currentPosition + 30000) }, text = "+30s")
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
