package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun ContentBankToolbar(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    activeContentFilter: ContentType?,
    onFilterSelected: (ContentType?) -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenYouTube: () -> Unit,
) {
    Column {
        Text("The Content Bank", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Intentional consumption queue. Rank your media.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IndustrialTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Search bank...") },
                singleLine = true,
            )
            if (searchQuery.isNotEmpty()) {
                IndustrialButton(onClick = { onSearchChanged("") }, text = "Clear")
            }
            IndustrialButton(onClick = onOpenYouTube, text = "YouTube")
            IndustrialButton(onClick = onOpenPodcasts, text = "Podcasts")
        }

        // Filtering Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters =
                listOf(
                    null,
                    ContentType.VIDEO,
                    ContentType.AUDIO,
                    ContentType.READING,
                )
            val labels = listOf("All", "Video", "Audio", "Read")

            filters.forEachIndexed { index, type ->
                FilterChip(
                    selected = activeContentFilter == type,
                    onClick = { onFilterSelected(type) },
                    label = { Text(labels[index]) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
        }
    }
}
