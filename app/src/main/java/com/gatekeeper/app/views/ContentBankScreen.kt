package com.gatekeeper.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gatekeeper.app.GatekeeperStateManager
import com.gatekeeper.app.domain.ContentItem
import com.gatekeeper.app.domain.ContentSource
import com.gatekeeper.app.domain.GatekeeperAction

@Suppress("FunctionName")
@Composable
fun ContentBankScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    // Sort by rank to ensure the Priority Matrix rules are followed
    val items = state.contentItems.sortedBy { it.rank }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("The Content Bank", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Intentional consumption queue. Rank your media.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Bank is empty. Share a link to Gatekeeper to capture it.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        ContentItemCard(
                            item = item,
                            index = index,
                            isFirst = index == 0,
                            isLast = index == items.size - 1
                        )
                    }
                }
            }
        }

        // Render the CleanPlayer Modal when a video is selected
        if (state.activeVideoId != null) {
            CleanPlayerModal(
                videoId = state.activeVideoId!!,
                onClose = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.CloseCleanPlayer)
                },
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ContentItemCard(item: ContentItem, index: Int, isFirst: Boolean, isLast: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Controls (Brutalist Reordering)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.ReorderContentBank(index, index - 1)) },
                    enabled = !isFirst
                ) { Text("▲") }
                Text("#${item.rank + 1}", fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.ReorderContentBank(index, index + 1)) },
                    enabled = !isLast
                ) { Text("▼") }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.source.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Action Buttons
            Column(horizontalAlignment = Alignment.End) {
                if (item.source == ContentSource.YOUTUBE) {
                    Button(onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanPlayer(item.videoId)) }) {
                        Text("Play")
                    }
                }
                TextButton(onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.RemoveFromContentBank(item.id)) }) {
                    Text("Drop", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
