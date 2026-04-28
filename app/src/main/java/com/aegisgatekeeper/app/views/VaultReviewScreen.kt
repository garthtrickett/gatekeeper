package com.aegisgatekeeper.app.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.VaultItem
import com.aegisgatekeeper.app.domain.isVaultUnlocked
import kotlinx.coroutines.delay
import java.time.LocalTime

@Suppress("FunctionName")
@Composable
fun VaultReviewScreen(
    overrideTime: LocalTime? = null,
    onNavigateToWeb: () -> Unit = {},
) {
    val state by GatekeeperStateManager.state.collectAsState()
    var currentTime by remember { mutableStateOf(overrideTime ?: LocalTime.now()) }
    var showFeedManagement by remember { mutableStateOf(false) }
    var feedSearchQuery by remember { mutableStateOf<String?>(null) }

    // Only auto-update if we aren't overriding the time for tests
    if (overrideTime == null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000 * 60) // Check every minute
                currentTime = LocalTime.now()
            }
        }
    }

    val isUnlocked =
        com.aegisgatekeeper.app.domain
            .isVaultUnlocked(currentTime, state.gatheringStartMinutes, state.gatheringEndMinutes)
    // Transform, don't mutate: Filter only unresolved items
    val unresolvedItems = state.vaultItems.filter { !it.isResolved && !it.isDeleted }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            Text(text = "Lookup Vault", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))

            if (isUnlocked) {
                VaultList(
                    items = unresolvedItems,
                    gatheringEndMinutes = state.gatheringEndMinutes,
                    onNavigateToWeb = onNavigateToWeb,
                    onOpenPodcasts = { query ->
                        feedSearchQuery = query
                        showFeedManagement = true
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LockedVaultMessage(state.gatheringStartMinutes, Modifier.weight(1f))
            }
        }
    }

    if (showFeedManagement) {
        FeedManagementDialog(
            onDismiss = {
                showFeedManagement = false
                feedSearchQuery = null
            },
            initialSearchQuery = feedSearchQuery,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun VaultList(
    items: List<VaultItem>,
    gatheringEndMinutes: Int,
    onNavigateToWeb: () -> Unit,
    onOpenPodcasts: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "The Gathering Phase ends at ${com.aegisgatekeeper.app.domain.formatMinutesToAmPm(
                gatheringEndMinutes,
            )}. Review your captured thoughts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (items.isEmpty()) {
            Text(
                text = "Vault is empty. Good job staying focused!",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) {
                    VaultItemCard(
                        item = it,
                        onNavigateToWeb = onNavigateToWeb,
                        onOpenPodcasts = onOpenPodcasts,
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun VaultItemCard(
    item: VaultItem,
    onNavigateToWeb: () -> Unit,
    onOpenPodcasts: (String) -> Unit,
) {
    TerminalPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.query,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.MarkVaultItemResolved(item.id, System.currentTimeMillis()))
                    },
                    text = "Resolved",
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.MarkVaultItemResolved(item.id, System.currentTimeMillis()))
                        val encoded = java.net.URLEncoder.encode(item.query, "UTF-8")
                        GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested("https://duckduckgo.com/?q=$encoded"))
                        onNavigateToWeb()
                    },
                    text = "🌐 Web",
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.MarkVaultItemResolved(item.id, System.currentTimeMillis()))
                        val encoded = java.net.URLEncoder.encode(item.query, "UTF-8")
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.ShowSurgicalSearch("https://m.youtube.com/results?search_query=$encoded"),
                        )
                    },
                    text = "🎬 YouTube",
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(GatekeeperAction.MarkVaultItemResolved(item.id, System.currentTimeMillis()))
                        onOpenPodcasts(item.query)
                    },
                    text = "🎙️ Podcasts",
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun LockedVaultMessage(
    gatheringStartMinutes: Int,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation) {
        LaunchedEffect(showConfirmation) {
            delay(2000L)
            showConfirmation = false
        }
    }

    val onSave = {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            GatekeeperStateManager.dispatch(
                GatekeeperAction.SaveToVault(trimmed, System.currentTimeMillis()),
            )
            query = ""
            showConfirmation = true
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🔒", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You are in Focus Mode",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your thoughts are captured.\nThe Gathering Phase begins at ${com.aegisgatekeeper.app.domain.formatMinutesToAmPm(
                gatheringStartMinutes,
            )}.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        IndustrialTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Dump a thought...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
            keyboardActions =
                androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onSave() },
                ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        IndustrialButton(
            onClick = onSave,
            enabled = query.trim().isNotEmpty() && !showConfirmation,
            modifier = Modifier.fillMaxWidth(),
            text = if (showConfirmation) "SAVED ✓" else "Save to Vault",
            invertEnabledColor = showConfirmation,
        )
    }
}
