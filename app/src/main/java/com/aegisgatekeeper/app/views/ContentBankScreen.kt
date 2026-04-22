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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalTime

@Suppress("FunctionName")
@Composable
fun ContentBankScreen(overrideTime: LocalTime? = null) {
    val state by GatekeeperStateManager.state.collectAsState()

    if (!state.isProTier) {
        PaywallScreen(
            title = "The Priority Matrix",
            description =
                "The Free tier includes the Lookup Vault and Layer Alpha. Upgrade to Pro to unlock " +
                    "the Sovereign Media Queue, Drag-and-Drop ranking, and the Surgical YouTube Engine.",
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }

    val items =
        state.contentItems
            .filter { (state.activeContentFilter == null || it.type == state.activeContentFilter) && !it.isDeleted }
            .filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                (it.channelName?.contains(searchQuery, ignoreCase = true) == true)
            }
            .sortedBy { it.rank }

    val lazyListState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var overscrollJob by remember { mutableStateOf<Job?>(null) }

    // Deep Work & Friction State
    val currentTime by remember { mutableStateOf(overrideTime ?: LocalTime.now()) }
    val isDeepWork =
        com.aegisgatekeeper.app.domain
            .isDeepWorkHours(currentTime)
    var isEditingUnlocked by remember { mutableStateOf(false) }
    var showFriction by remember { mutableStateOf(false) }
    var pendingFilterAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndustrialTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Search bank...") },
                        singleLine = true,
                    )
                    if (searchQuery.isNotEmpty()) {
                        IndustrialButton(onClick = { searchQuery = "" }, text = "Clear")
                    }
                }

                // Filtering Chips
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeFilter = state.activeContentFilter
                    val filters =
                        listOf(
                            null,
                            com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                            com.aegisgatekeeper.app.domain.ContentType.AUDIO,
                            com.aegisgatekeeper.app.domain.ContentType.READING,
                        )
                    val labels = listOf("All", "Video", "Audio", "Read")

                    filters.forEachIndexed { index, type ->
                        FilterChip(
                            selected = activeFilter == type,
                            onClick = {
                                val action = { GatekeeperStateManager.dispatch(GatekeeperAction.UpdateContentFilter(type)) }
                                if (isDeepWork && !isEditingUnlocked && activeFilter != type) {
                                    pendingFilterAction = action
                                    showFriction = true
                                } else {
                                    action()
                                }
                            },
                            label = { Text(labels[index]) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                    }
                }

                if (state.contentItems.none { !it.isDeleted }) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Bank is empty. Share a link to Gatekeeper to capture it.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
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
                                        if (isDeepWork && !isEditingUnlocked) {
                                            showFriction = true
                                        } else {
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
                                                    GatekeeperStateManager.dispatch(
                                                        GatekeeperAction.ReorderContentBank(
                                                            dragged,
                                                            item.index,
                                                            System.currentTimeMillis(),
                                                        ),
                                                    )
                                                    draggedItemIndex = item.index
                                                } else if (draggedItem.index > item.index && draggedItemCenter < itemCenter) {
                                                    GatekeeperStateManager.dispatch(
                                                        GatekeeperAction.ReorderContentBank(
                                                            dragged,
                                                            item.index,
                                                            System.currentTimeMillis(),
                                                        ),
                                                    )
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
                                savedPosition = state.savedMediaPositions[item.videoId],
                                modifier =
                                    Modifier.graphicsLayer {
                                        translationY = if (isBeingDragged) dragOffset else 0f
                                        shadowElevation = elevation
                                    },
                            )
                        }
                    }
                }
            } // Close Column

            // Capture Button
            if (state.isProcessingLink) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(12.dp)
                                .semantics { contentDescription = "Processing Link" },
                        strokeWidth = 3.dp,
                    )
                }
            } else {
                IndustrialButton(
                    onClick = { showAddDialog = true },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    text = "+",
                )
            }
        } // Close Box

        // Friction Modal
        if (showFriction) {
            Dialog(
                onDismissRequest = { showFriction = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BallBalancingUi(
                    title = "Deep Work Interruption",
                    subtitle = "Complete this task to unlock list editing.",
                    onSuccess = {
                        isEditingUnlocked = true
                        showFriction = false
                        pendingFilterAction?.invoke()
                        pendingFilterAction = null
                    },
                    onClose = { showFriction = false },
                )
            }
        }

        if (showAddDialog) {
            AddLinkDialog(
                onDismiss = { showAddDialog = false },
                onSave = { url ->
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.ProcessSharedLink(url = url, currentTimestamp = System.currentTimeMillis()),
                    )
                    showAddDialog = false
                },
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun AddLinkDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var url by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val clipboardText = clipboardManager.getText()?.text
            if (clipboardText != null) {
                val isYouTube =
                    clipboardText.contains("youtu.be", ignoreCase = true) ||
                        clipboardText.contains("youtube.com", ignoreCase = true)
                val isSoundCloud = clipboardText.contains("soundcloud.com", ignoreCase = true)

                if (isYouTube || isSoundCloud) {
                    val urlRegex = """(https?://[^\s"'<>]+)""".toRegex()
                    val extractedUrl = urlRegex.find(clipboardText)?.value
                    if (extractedUrl != null) {
                        url = extractedUrl
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore clipboard access errors gracefully
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    androidx.compose.ui.Modifier
                        .padding(24.dp),
            ) {
                Text("Add to Bank", style = MaterialTheme.typography.titleLarge)
                Spacer(
                    modifier =
                        androidx.compose.ui.Modifier
                            .height(16.dp),
                )
                IndustrialTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Paste YouTube or SoundCloud link") },
                    modifier =
                        androidx.compose.ui.Modifier
                            .fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(
                    modifier =
                        androidx.compose.ui.Modifier
                            .height(24.dp),
                )
                Row(
                    modifier =
                        androidx.compose.ui.Modifier
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(
                        modifier =
                            androidx.compose.ui.Modifier
                                .width(8.dp),
                    )
                    IndustrialButton(
                        onClick = { onSave(url) },
                        enabled = url.isNotBlank(),
                        text = "Add Intent",
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ContentItemCard(
    item: ContentItem,
    savedPosition: Float? = null,
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
                    val decodedTitle = item.title
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
                            overflow = TextOverflow.Ellipsis
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
                Column(horizontalAlignment = Alignment.End) {
                    if (item.source == ContentSource.YOUTUBE || item.source == ContentSource.SOUNDCLOUD ||
                        item.type == ContentType.READING
                    ) {
                        IndustrialButton(
                            onClick = {
                                when (item.source) {
                                    ContentSource.YOUTUBE -> {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanPlayer(item.videoId))
                                    }

                                    ContentSource.SOUNDCLOUD -> {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanAudioPlayer(item.videoId))
                                    }

                                    ContentSource.SUBSTACK, ContentSource.GENERIC -> {
                                        val intent =
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(item.videoId),
                                            )
                                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        com.aegisgatekeeper.app.App.instance
                                            .startActivity(intent)
                                    }
                                }
                            },
                            text = if (item.type == ContentType.READING) "Read" else "Play",
                        )
                    }
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.RemoveFromContentBank(item.id, System.currentTimeMillis()),
                            )
                        },
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
                    trackColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            }
        }
    }
}
