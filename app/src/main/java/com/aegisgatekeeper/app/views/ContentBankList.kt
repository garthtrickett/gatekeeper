package com.aegisgatekeeper.app.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.DownloadStatus
import com.aegisgatekeeper.app.domain.IndustrialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Suppress("FunctionName")
@Composable
fun ContentBankList(
    items: List<ContentItem>,
    hasAnyItems: Boolean,
    savedMediaPositions: Map<String, Float>,
    activeDownloads: Map<String, Float>,
    extractingMediaId: String?,
    onReorder: (Int, Int) -> Unit,
    onDragStart: () -> Boolean,
    onDownloadRequested: (String) -> Unit,
    onDeleteDownloadedMedia: (String) -> Unit,
    onPlayContent: (ContentItem) -> Unit,
    onDropContent: (String) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var overscrollJob by remember { mutableStateOf<Job?>(null) }

    if (!hasAnyItems) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Bank is empty. Share a link to Gatekeeper to capture it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    } else if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No content matches your search.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            if (onDragStart()) {
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                    ?.also {
                                        draggedItemIndex = it.index
                                    }
                            }
                        },
                        onDragEnd = {
                            overscrollJob?.cancel()
                            draggedItemIndex = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            overscrollJob?.cancel()
                            draggedItemIndex = null
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y

                            val dragged = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                            val draggedItem =
                                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragged }
                                    ?: return@detectDragGesturesAfterLongPress
                            val draggedItemCenter = draggedItem.offset + draggedItem.size / 2f + dragOffset

                            // Check for swaps
                            lazyListState.layoutInfo.visibleItemsInfo
                                .filter { it.index != dragged }
                                .forEach { item ->
                                    val itemCenter = item.offset + item.size / 2f
                                    if (draggedItem.index < item.index && draggedItemCenter > itemCenter) {
                                        onReorder(dragged, item.index)
                                        draggedItemIndex = item.index
                                    } else if (draggedItem.index > item.index && draggedItemCenter < itemCenter) {
                                        onReorder(dragged, item.index)
                                        draggedItemIndex = item.index
                                    }
                                }

                            // Autoscroll
                            val listBounds = lazyListState.layoutInfo.viewportSize.height
                            overscrollJob?.cancel()
                            if (draggedItemCenter > listBounds - 200) {
                                overscrollJob = scope.launch { lazyListState.scrollBy(dragAmount.y) }
                            } else if (draggedItemCenter < 200) {
                                overscrollJob = scope.launch { lazyListState.scrollBy(dragAmount.y) }
                            }
                        },
                    )
                },
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isBeingDragged = index == draggedItemIndex
                                val elevation by animateFloatAsState(if (isBeingDragged) 8f else 0f, label = "elevation")
                ContentItemCard(
                    item = item,
                    savedPosition = savedMediaPositions[item.videoId],
                    activeDownloadProgress = activeDownloads[item.id],
                    isExtracting = extractingMediaId == item.id,
                    onDownloadRequested = onDownloadRequested,
                    onDeleteDownloadedMedia = onDeleteDownloadedMedia,
                    onPlayContent = onPlayContent,
                    onDropContent = onDropContent,
                    modifier =
                        Modifier.graphicsLayer {
                            translationY = if (isBeingDragged) dragOffset else 0f
                            shadowElevation = elevation
                        },
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ContentItemCard(
    item: ContentItem,
    savedPosition: Float? = null,
    activeDownloadProgress: Float? = null,
    isExtracting: Boolean = false,
    onDownloadRequested: (String) -> Unit,
    onDeleteDownloadedMedia: (String) -> Unit,
    onPlayContent: (ContentItem) -> Unit,
    onDropContent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TerminalPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${item.rank + 1}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Content Details
                Column(modifier = Modifier.weight(1f)) {
                    val decodedTitle =
                        item.title
                            .replace("&amp;", "&")
                            .replace("&#39;", "'")
                            .replace("&quot;", "\"")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")

                    Text(
                        text = decodedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.channelName != null) {
                        Text(
                            text = item.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val durationText = item.durationSeconds?.let { " • ${it / 60}m" } ?: ""
                    Text(
                        text = "${item.source.name}$durationText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Action Buttons
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.type == ContentType.AUDIO && item.source != ContentSource.SOUNDCLOUD) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val status = item.downloadStatus
                            val progress = activeDownloadProgress ?: 0f
                            if (status == DownloadStatus.DOWNLOADING ||
                                status == DownloadStatus.QUEUED
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    if (progress > 0f && progress < 100f) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            progress = { progress / 100f },
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            } else if (status == DownloadStatus.COMPLETED) {
                                Text("✅", modifier = Modifier.padding(end = 8.dp))
                                IndustrialButton(
                                    onClick = { onDeleteDownloadedMedia(item.id) },
                                    text = "Delete Offline File",
                                    isWarning = true,
                                )
                            } else {
                                IndustrialButton(
                                    onClick = { onDownloadRequested(item.id) },
                                    text = "Download",
                                )
                            }
                        }
                    }
                    if (item.source == ContentSource.YOUTUBE || item.source == ContentSource.SOUNDCLOUD ||
                        item.type == ContentType.READING || item.type == ContentType.AUDIO
                    ) {
                        IndustrialButton(
                            onClick = { onPlayContent(item) },
                            text = if (item.type == ContentType.READING) "Read" else "Play",
                            isLoading = isExtracting,
                            enabled = !isExtracting,
                        )
                    }
                    IndustrialButton(
                        onClick = { onDropContent(item.id) },
                        text = "Drop",
                        isWarning = true,
                    )
                }
            } // Close Row

            if (savedPosition != null && savedPosition > 0f && item.durationSeconds != null && item.durationSeconds > 0) {
                val progress = (savedPosition / item.durationSeconds).coerceIn(0f, 1f)
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
