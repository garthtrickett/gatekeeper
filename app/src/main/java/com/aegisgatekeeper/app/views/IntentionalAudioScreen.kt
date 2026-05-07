// app/src/main/java/com/aegisgatekeeper/app/views/IntentionalAudioScreen.kt

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
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

    if (!state.sync.isProTier) {
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
                val item = state.data.intentionalSlots.find { it.slotIndex == i }
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
                                val decodedTitle =
                                    item
                                        ?.contentItem
                                        ?.title
                                        ?.replace("&amp;", "&")
                                        ?.replace("&#39;", "'")
                                        ?.replace("&quot;", "\"")
                                        ?.replace("&lt;", "<")
                                        ?.replace("&gt;", ">")

                                Text(
                                    text = decodedTitle ?: "Empty Slot ${i + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                if (item?.contentItem?.channelName != null) {
                                    Text(
                                        text = item.contentItem.channelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                if (item != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val durationText = item.contentItem.durationSeconds?.let { " • ${it / 60}m" } ?: ""
                                    Text(
                                        text = "${item.contentItem.type.name} • ${item.contentItem.source.name}$durationText",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (item != null) {
                                    IndustrialButton(
                                        onClick = {
                                            when (item.contentItem.type) {
                                                com.aegisgatekeeper.app.domain.ContentType.VIDEO -> {
                                                    if (item.contentItem.source == com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE) {
                                                        GatekeeperStateManager.dispatch(
                                                            GatekeeperAction.OpenSurgicalYouTube(
                                                                "https://m.youtube.com/watch?v=${item.contentItem.videoId}",
                                                            ),
                                                        )
                                                    } else {
                                                        GatekeeperStateManager.dispatch(
                                                            GatekeeperAction.ExtractAndPlayMedia(item.contentItem),
                                                        )
                                                    }
                                                }

                                                com.aegisgatekeeper.app.domain.ContentType.AUDIO -> {
                                                    if (item.contentItem.source == com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE) {
                                                        GatekeeperStateManager.dispatch(
                                                            GatekeeperAction.OpenSurgicalYouTube(
                                                                "https://m.youtube.com/watch?v=${item.contentItem.videoId}",
                                                            ),
                                                        )
                                                    } else if (item.contentItem.source ==
                                                        com.aegisgatekeeper.app.domain.ContentSource.SOUNDCLOUD
                                                    ) {
                                                        GatekeeperStateManager.dispatch(
                                                            GatekeeperAction.ExtractAndPlayMedia(item.contentItem),
                                                        )
                                                    } else {
                                                        GatekeeperStateManager.dispatch(
                                                            GatekeeperAction.OpenNativePlayer(item.contentItem),
                                                        )
                                                    }
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
                                        modifier = Modifier.width(90.dp),
                                    )
                                }
                                IndustrialButton(
                                    onClick = {
                                        showFrictionForSlot = i
                                    },
                                    text = if (item != null) "Eject" else "Insert",
                                    isWarning = item != null,
                                    modifier = Modifier.width(90.dp),
                                )
                            }
                        }

                        if (item != null) {
                            val savedPosition = state.media.savedMediaPositions[item.contentItem.videoId]
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
                            state.data.contentItems.filter { content ->
                                state.data.intentionalSlots.none { it.contentItem.id == content.id }
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
                            val currentItem = state.data.intentionalSlots.find { it.slotIndex == slotToEdit }
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
