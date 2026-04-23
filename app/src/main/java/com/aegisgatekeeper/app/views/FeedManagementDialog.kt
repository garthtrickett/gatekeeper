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
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun FeedManagementDialog(onDismiss: () -> Unit) {
    val state by GatekeeperStateManager.state.collectAsState()
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(500.dp).padding(16.dp),
        ) {
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

                Spacer(modifier = Modifier.height(24.dp))

                if (state.podcastSubscriptions.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No podcast subscriptions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.podcastSubscriptions, key = { it.id }) { sub ->
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
                                        modifier =
                                            Modifier
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
    }
}
