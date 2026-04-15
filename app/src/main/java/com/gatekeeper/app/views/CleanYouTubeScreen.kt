package com.gatekeeper.app.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gatekeeper.app.GatekeeperStateManager
import com.gatekeeper.app.api.YoutubeSearchItem
import com.gatekeeper.app.domain.GatekeeperAction

@Suppress("FunctionName")
@Composable
fun CleanYouTubeScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var query by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Surgical Search", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Find exactly what you need. No rabbit holes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search for intentional content...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                GatekeeperStateManager.dispatch(GatekeeperAction.SearchYouTubeRequested(query))
                            }
                        },
                    ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (state.isLoadingYouTube) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.youtubeSearchResults.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.youtubeSearchResults, key = { it.id.videoId }) { item ->
                        YouTubeResultItem(item = item) {
                            GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanPlayer(item.id.videoId))
                        }
                    }
                }
            } else if (query.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No results to display.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Render the CleanPlayer Modal when a video is selected
        if (state.activeVideoId != null) {
            CleanPlayerModal(
                videoId = state.activeVideoId!!,
                onClose = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.CloseCleanPlayer)
                }
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun YouTubeResultItem(
    item: YoutubeSearchItem,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.snippet.thumbnails.high.url,
                contentDescription = "Thumbnail for ${item.snippet.title}",
                modifier =
                    Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                val decodedTitle =
                    android.text.Html
                        .fromHtml(item.snippet.title, android.text.Html.FROM_HTML_MODE_LEGACY)
                        .toString()
                Text(
                    text = decodedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.snippet.channelTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
