package com.aegisgatekeeper.app.views.interception

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.views.BallBalancingUi
import com.aegisgatekeeper.app.views.MovingCloseButton

// --- NEW NAVIGATIONAL COMPOSABLE ---
@Suppress("FunctionName")
@Composable
fun InterceptionScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    val interceptedPackage = state.currentlyInterceptedApp ?: return

    // This local state determines which screen to show: CHOICE, BYPASS, SWAP, or FRICTION
    var screen by remember(state.expiredSessionDurationMillis) {
        mutableStateOf(if (state.expiredSessionDurationMillis != null) "SWAP" else "CHOICE")
    }
    var selectedTimeMillis by remember { mutableStateOf(15 * 60_000L) }

    when (screen) {
        "SWAP" -> {
            val maxMins = ((state.expiredSessionDurationMillis ?: 0L) / 60000).toInt()
            TimeBoxSwapUi(
                maxMinutes = maxMins,
                items = state.contentItems,
                onPlayVideo = { videoId ->
                    val intent =
                        android.content.Intent(com.aegisgatekeeper.app.App.instance, MainActivity::class.java).apply {
                            putExtra("OPEN_CLEAN_PLAYER_VIDEO_ID", videoId)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    com.aegisgatekeeper.app.App.instance
                        .startActivity(intent)
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
                onPlayAudio = { audioUrl ->
                    val intent =
                        android.content.Intent(com.aegisgatekeeper.app.App.instance, MainActivity::class.java).apply {
                            putExtra("OPEN_CLEAN_AUDIO_URL", audioUrl)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    com.aegisgatekeeper.app.App.instance
                        .startActivity(intent)
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
                onOpenLink = { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    com.aegisgatekeeper.app.App.instance
                        .startActivity(intent)
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
                onCancel = { screen = "CHOICE" },
            )
        }

        "CHOICE" -> {
            InterceptionChoiceUi(
                interceptedPackage = interceptedPackage,
                customMessage = state.activeBlockReason ?: state.customMessages[interceptedPackage],
                contentItems = state.contentItems,
                onBypass = { time ->
                    selectedTimeMillis = time
                    screen = "BYPASS"
                },
                onFriction = { time ->
                    selectedTimeMillis = time
                    screen = "FRICTION"
                },
                onHabits = { screen = "HABITS" },
                onPlayContent = { item ->
                    if (item.type == com.aegisgatekeeper.app.domain.ContentType.VIDEO) {
                        val intent =
                            android.content.Intent(com.aegisgatekeeper.app.App.instance, MainActivity::class.java).apply {
                                putExtra("OPEN_CLEAN_PLAYER_VIDEO_ID", item.videoId)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        com.aegisgatekeeper.app.App.instance
                            .startActivity(intent)
                    } else if (item.type == com.aegisgatekeeper.app.domain.ContentType.AUDIO) {
                        val intent =
                            android.content.Intent(com.aegisgatekeeper.app.App.instance, MainActivity::class.java).apply {
                                putExtra("OPEN_CLEAN_AUDIO_URL", item.videoId)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        com.aegisgatekeeper.app.App.instance
                            .startActivity(intent)
                    } else {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.videoId))
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        com.aegisgatekeeper.app.App.instance
                            .startActivity(intent)
                    }
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
            )
        }

        "BYPASS" -> {
            EmergencyBypassUi(
                interceptedPackage = interceptedPackage,
                allocatedTimeMillis = selectedTimeMillis,
            )
        }

        "FRICTION" -> {
            BallBalancingUi(
                onSuccess = {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.FrictionCompleted(
                            packageName = interceptedPackage,
                            allocatedDurationMillis = selectedTimeMillis,
                            currentTimestamp = System.currentTimeMillis(),
                        ),
                    )
                },
                onClose = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.LogGiveUp(interceptedPackage, System.currentTimeMillis()))
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
            )
        }

        "HABITS" -> {
            AlternativeSuggestionUi(
                activities = state.alternativeActivities,
                onSelectActivity = { _ ->
                    GatekeeperStateManager.dispatch(GatekeeperAction.LogGiveUp(interceptedPackage, System.currentTimeMillis()))
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
                onCancel = { screen = "CHOICE" }
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun InterceptionChoiceUi(
    interceptedPackage: String,
    customMessage: String? = null,
    contentItems: List<ContentItem> = emptyList(),
    onBypass: (Long) -> Unit,
    onFriction: (Long) -> Unit,
    onHabits: () -> Unit = {},
    onPlayContent: (ContentItem) -> Unit = {},
) {
    val state by GatekeeperStateManager.state.collectAsState()
    var step by remember { mutableStateOf("PROMPT") }
    var selectedTimeMillis by remember { mutableStateOf(30 * 60_000L) }
    var selectedCuratedRangeIndex by remember { androidx.compose.runtime.mutableIntStateOf(2) } // Default to 10-30m

    val timeOptions =
        listOf(
            5 * 60_000L to "5m",
            10 * 60_000L to "10m",
            15 * 60_000L to "15m",
            30 * 60_000L to "30m",
            45 * 60_000L to "45m",
            60 * 60_000L to "1h",
            120 * 60_000L to "2h",
        )

    val curatedRanges =
        listOf(
            "0-5m" to 0L..300L,
            "5-10m" to 301L..600L,
            "10-30m" to 601L..1800L,
            "30m-1h" to 1801L..3600L,
            "1-2h" to 3601L..7200L,
            "2h+" to 7201L..Long.MAX_VALUE,
        )

    val appName = getAppName(interceptedPackage)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        ) {
            // The randomized position "Close" button to break muscle memory
            MovingCloseButton(onClose = {
                GatekeeperStateManager.dispatch(GatekeeperAction.LogGiveUp(interceptedPackage, System.currentTimeMillis()))
                GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
            })

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (step == "PROMPT") {
                    Text(
                        text = customMessage ?: "Take a breath.",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "You are about to open $appName.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    IndustrialButton(
                        onClick = {
                            selectedTimeMillis = 30 * 60_000L
                            step = "CURATED"
                        },
                        text = "Consume curated content instead",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    IndustrialButton(
                        onClick = onHabits,
                        text = "Choose a positive habit",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    IndustrialButton(
                        onClick = {
                            selectedTimeMillis = 15 * 60_000L
                            step = "UNLOCK"
                        },
                        text = "Continue to $appName",
                        isWarning = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (step == "CURATED") {
                    Text(
                        text = "How long do you want to spend here?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(curatedRanges.size) { index ->
                            val (label, _) = curatedRanges[index]
                            FilterChip(
                                selected = selectedCuratedRangeIndex == index,
                                onClick = { selectedCuratedRangeIndex = index },
                                label = { Text(label) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    val selectedRange = curatedRanges[selectedCuratedRangeIndex].second
                    val targetItems =
                        contentItems
                            .filter {
                                it.durationSeconds != null && it.durationSeconds in selectedRange
                            }.sortedBy { it.rank }

                    if (targetItems.isEmpty()) {
                        Text(
                            "No curated content found for ${curatedRanges[selectedCuratedRangeIndex].first}.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(targetItems) { item ->
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayContent(item) },
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "⏱️ ${item.durationSeconds!! / 60}m",
                                                color = Color.Cyan,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    item.title,
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                )
                                                if (item.channelName != null) {
                                                    Text(
                                                        item.channelName,
                                                        color = Color.Gray,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        val savedPosition = state.savedMediaPositions[item.videoId]
                                        if (savedPosition != null && savedPosition > 0f && item.durationSeconds != null &&
                                            item.durationSeconds > 0
                                        ) {
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
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = { step = "PROMPT" }, text = "Back")
                } else if (step == "UNLOCK") {
                    IndustrialButton(
                        onClick = { onFriction(selectedTimeMillis) },
                        text = "Continue with friction",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    IndustrialButton(
                        onClick = { onBypass(selectedTimeMillis) },
                        text = "Emergency Bypass",
                        isWarning = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = { step = "PROMPT" }, text = "Back")
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun TimeBoxSwapUi(
    maxMinutes: Int,
    items: List<ContentItem>,
    onPlayVideo: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    onOpenLink: (String) -> Unit = {},
    onCancel: () -> Unit,
) {
    val state by GatekeeperStateManager.state.collectAsState()
    val validItems =
        items
            .filter {
                (it.durationSeconds == null || it.durationSeconds <= maxMinutes * 60)
            }.sortedBy { it.rank }
            .take(3)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (validItems.isEmpty()) {
                    Text(
                        "No saved content under $maxMinutes minutes.",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Don't fill the void with junk. Breathe, or give up.", color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = onCancel, text = "Go Back")
                } else {
                    Text("Intentional Swap", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Watch this instead:", color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))

                    validItems.forEach { item ->
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                                    if (item.type == com.aegisgatekeeper.app.domain.ContentType.VIDEO) {
                                        onPlayVideo(item.videoId)
                                    } else if (item.type == com.aegisgatekeeper.app.domain.ContentType.AUDIO) {
                                        onPlayAudio(item.videoId)
                                    } else if (item.type == com.aegisgatekeeper.app.domain.ContentType.READING) {
                                        onOpenLink(item.videoId)
                                    }
                                },
                            colors =
                                CardDefaults
                                    .cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val durationStr = if (item.durationSeconds != null) "${item.durationSeconds / 60}m" else "Unknown"
                                    Text("⏱️ $durationStr", color = Color.Cyan, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(item.title, color = Color.White, maxLines = 2)
                                        if (item.channelName != null) {
                                            Text(item.channelName, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                                val savedPosition = state.savedMediaPositions[item.videoId]
                                if (savedPosition != null && savedPosition > 0f && item.durationSeconds != null &&
                                    item.durationSeconds > 0
                                ) {
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
                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = onCancel, text = "Cancel Swap", isWarning = true)
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun EmergencyBypassUi(
    interceptedPackage: String,
    allocatedTimeMillis: Long,
) {
    var reason by remember { mutableStateOf("") }
    val appName = getAppName(interceptedPackage)
    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .verticalScroll(scrollState)
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Emergency Bypass",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Why do you need to open $appName?",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(48.dp))
            IndustrialTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("e.g. 'I need an Uber'") },
                modifier = Modifier.width(300.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            IndustrialButton(
                onClick = {
                    val trimmedReason = reason.trim()
                    if (trimmedReason.isNotBlank()) {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.EmergencyBypassRequested(
                                packageName = interceptedPackage,
                                reason = trimmedReason,
                                allocatedDurationMillis = allocatedTimeMillis,
                                currentTimestamp = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                enabled = reason.trim().isNotBlank(),
                text = "Unlock for ${allocatedTimeMillis / 60000} minutes",
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun AlternativeSuggestionUi(
    activities: List<com.aegisgatekeeper.app.domain.AlternativeActivity>,
    onSelectActivity: (com.aegisgatekeeper.app.domain.AlternativeActivity) -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (activities.isEmpty()) {
                    Text(
                        "No alternative activities configured.",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Go to the Habits tab to add some.", color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = onCancel, text = "Go Back")
                } else {
                    Text("Positive Habits", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do one of these instead:", color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                        items(activities) { activity ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelectActivity(activity)
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏃", fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(activity.description, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    IndustrialButton(onClick = onCancel, text = "Cancel", isWarning = true)
                }
            }
        }
    }
}

@Composable
fun getAppName(packageName: String): String {
    val context = LocalContext.current
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        val parts = packageName.split('.')
        val name = parts.lastOrNull { it != "com" && it != "android" && it != "app" && it != "org" && it != "net" } ?: parts.last()
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
