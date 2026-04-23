package com.aegisgatekeeper.app.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun FeedManagementDialog(onDismiss: () -> Unit) {
    val state by GatekeeperStateManager.state.collectAsState()

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (state.activePodcastId != null) {
                GatekeeperStateManager.dispatch(GatekeeperAction.ClearPodcastEpisodes)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(600.dp).padding(16.dp),
        ) {
            if (state.activePodcastId != null) {
                PodcastEpisodesView(state, onDismiss)
            } else {
                PodcastSubscriptionsView(state, onDismiss)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun PodcastSubscriptionsView(state: GatekeeperState, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isNotEmpty() && state.podcastSyncError != null) {
            GatekeeperStateManager.dispatch(GatekeeperAction.ClearPodcastSyncError)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Manage Podcast Feeds", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IndustrialTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RSS Feed URL") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (state.isSyncingPodcasts) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                IndustrialButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            GatekeeperStateManager.dispatch(GatekeeperAction.ProcessPodcastUrl(url))
                            url = ""
                        }
                    },
                    enabled = url.isNotBlank(),
                    text = "Add",
                )
            }
        }

        if (state.podcastSyncError != null) {
            Text(
                text = state.podcastSyncError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.podcastSubscriptions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No podcast subscriptions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.podcastSubscriptions, key = { it.id }) { sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            GatekeeperStateManager.dispatch(GatekeeperAction.LoadPodcastEpisodes(sub.feedUrl, sub.id))
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.showTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    sub.feedUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🗑️",
                                modifier = Modifier
                                    .clickable {
                                        GatekeeperStateManager.dispatch(
                                            GatekeeperAction.RemovePodcastSubscription(sub.id, System.currentTimeMillis()),
                                        )
                                    }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IndustrialButton(onClick = onDismiss, text = "Close")
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun PodcastEpisodesView(state: GatekeeperState, onDismiss: () -> Unit) {
    val activeSub = state.podcastSubscriptions.find { it.id == state.activePodcastId }
    val title = activeSub?.showTitle ?: "Podcast Episodes"

    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoadingEpisodes) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        } else if (state.activePodcastEpisodes.isNullOrEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No episodes found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.activePodcastEpisodes!!, key = { it.audioUrl }) { ep ->
                    val isAlreadyInBank = state.contentItems.any { it.videoId == ep.audioUrl && !it.isDeleted }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ep.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                val metaText = buildString {
                                    if (ep.pubDate != null) append(ep.pubDate)
                                    if (ep.pubDate != null && ep.durationSeconds != null && ep.durationSeconds > 0) append(" • ")
                                    if (ep.durationSeconds != null && ep.durationSeconds > 0) append("${ep.durationSeconds / 60}m")
                                }
                                if (metaText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        metaText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IndustrialButton(
                                onClick = {
                                    if (!isAlreadyInBank && state.activePodcastId != null) {
                                        GatekeeperStateManager.dispatch(
                                            GatekeeperAction.AddEpisodeToBank(ep, state.activePodcastId, activeSub?.showTitle ?: "Podcast")
                                        )
                                    }
                                },
                                enabled = !isAlreadyInBank,
                                text = if (isAlreadyInBank) "✓" else "+",
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IndustrialButton(onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.ClearPodcastEpisodes) }, text = "Back", isWarning = true)
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = {
                GatekeeperStateManager.dispatch(GatekeeperAction.ClearPodcastEpisodes)
                onDismiss()
            }, text = "Close")
        }
    }
}
