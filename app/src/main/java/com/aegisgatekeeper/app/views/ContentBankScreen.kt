package com.aegisgatekeeper.app.views

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import java.time.LocalTime

@Suppress("FunctionName")
@Composable
fun ContentBankScreen(overrideTime: LocalTime? = null) {
    val state by GatekeeperStateManager.state.collectAsState()

    if (!state.sync.isProTier) {
        PaywallScreen(
            title = "The Priority Matrix",
            description =
                "The Free tier includes the Lookup Vault and Layer Alpha. Upgrade to Pro to unlock " +
                    "the Sovereign Media Queue, Drag-and-Drop ranking, and the Surgical YouTube Engine.",
        )
        return
    }

    var activeContentFilter by remember { mutableStateOf<ContentType?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val items =
        state.data.contentItems
            .filter { (activeContentFilter == null || it.type == activeContentFilter) && !it.isDeleted }
            .filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.channelName?.contains(searchQuery, ignoreCase = true) == true)
            }.sortedBy { it.rank }

    // Deep Work & Friction State
    val currentTime by remember { mutableStateOf(overrideTime ?: LocalTime.now()) }
    val isDeepWork =
        com.aegisgatekeeper.app.domain
            .isDeepWorkHours(currentTime, state.data.deepWorkStartMinutes, state.data.deepWorkEndMinutes)
    var isEditingUnlocked by remember { mutableStateOf(false) }
    var showFriction by remember { mutableStateOf(false) }
    var pendingFilterAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFeedManagement by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                ContentBankToolbar(
                    searchQuery = searchQuery,
                    onSearchChanged = { searchQuery = it },
                    activeContentFilter = activeContentFilter,
                    onFilterSelected = { type ->
                        val action = { activeContentFilter = type }
                        if (isDeepWork && !isEditingUnlocked && activeContentFilter != type) {
                            pendingFilterAction = action
                            showFriction = true
                        } else {
                            action()
                        }
                    },
                    onOpenPodcasts = { showFeedManagement = true },
                    onOpenYouTube = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/results?search_query=podcasts"),
                        )
                    },
                )

                ContentBankList(
                    items = items,
                    hasAnyItems = state.data.contentItems.any { !it.isDeleted },
                    savedMediaPositions = state.media.savedMediaPositions,
                    activeDownloads = state.media.activeDownloads,
                    extractingMediaId = state.media.extractingMediaId,
                    onReorder = { from, to ->
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.ReorderContentBank(from, to, System.currentTimeMillis()),
                        )
                    },
                    onDragStart = {
                        if (isDeepWork && !isEditingUnlocked) {
                            showFriction = true
                            false
                        } else {
                            true
                        }
                    },
                    onDownloadRequested = { id ->
                        GatekeeperStateManager.dispatch(GatekeeperAction.DownloadMediaRequested(id))
                    },
                    onDeleteDownloadedMedia = { id ->
                        GatekeeperStateManager.dispatch(GatekeeperAction.DeleteDownloadedMedia(id))
                    },
                    onPlayContent = { item ->
                        when (item.source) {
                            ContentSource.YOUTUBE -> {
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.OpenCleanPlayer(item.videoId),
                                )
                            }

                            ContentSource.SOUNDCLOUD -> {
                                GatekeeperStateManager.dispatch(GatekeeperAction.ExtractAndPlayMedia(item))
                            }

                            ContentSource.SUBSTACK, ContentSource.GENERIC -> {
                                if (item.type == ContentType.AUDIO) {
                                    GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
                                } else {
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
                        }
                    },
                    onDropContent = { id ->
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.RemoveFromContentBank(id, System.currentTimeMillis()),
                        )
                    },
                )
            } // Close Column

            // Capture Button
            if (state.media.isProcessingLink) {
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

        if (showFeedManagement) {
            FeedManagementDialog(onDismiss = { showFeedManagement = false })
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add to Bank", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Paste YouTube or SoundCloud link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
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
