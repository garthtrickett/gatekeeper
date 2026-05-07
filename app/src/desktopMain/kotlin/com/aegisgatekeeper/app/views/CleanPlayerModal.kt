package com.aegisgatekeeper.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
actual fun CleanPlayerModal(
        videoId: String,
        isVisible: Boolean,
        onMinimize: () -> Unit,
        onStop: () -> Unit,
) {
    if (!isVisible) return

    Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Surgical Player (Desktop MVP)", style = MaterialTheme.typography.headlineMedium)
            Text("Video ID: $videoId", color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    "The IFrame Player API is currently optimized for Android.\nUse the 'Web' tab for filtered YouTube on Desktop.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IndustrialButton(onClick = onMinimize, text = "Minimize")
                IndustrialButton(onClick = onStop, text = "Close Player", isWarning = true)
            }
        }
    }
}
