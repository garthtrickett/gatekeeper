package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager

@Suppress("FunctionName")
@Composable
actual fun CleanPlayerModal(
    videoId: String,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Clean Player (Desktop MVP)")
            Spacer(modifier = Modifier.height(16.dp))
            com.aegisgatekeeper.app.domain
                .IndustrialButton(onClick = onClose, text = "Close Video")
        }
    }
}

@Suppress("FunctionName")
@Composable
actual fun PinnedWebModal(
    url: String,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pinned Web Modal: $url (Desktop MVP)")
            Spacer(modifier = Modifier.height(16.dp))
            com.aegisgatekeeper.app.domain
                .IndustrialButton(onClick = onClose, text = "Close")
        }
    }
}

@Suppress("FunctionName")
@Composable
fun ContentBankScreen() {
    val state by GatekeeperStateManager.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Content Bank (Desktop MVP)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.contentItems) { item ->
                val durationText = item.durationSeconds?.let { " (${it / 60}m)" } ?: ""
                Text("- ${item.title} [${item.type.name}]$durationText", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun VaultReviewScreen() {
    val state by GatekeeperStateManager.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lookup Vault (Desktop MVP)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.contentItems) { item ->
                val durationText = item.durationSeconds?.let { " (${it / 60}m)" } ?: ""
                Text("- ${item.title} [${item.type.name}]$durationText", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun CleanYouTubeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Surgical Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "The native YouTube client is an Android-only feature. Use the 'Web' tab to access a filtered version of YouTube.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
