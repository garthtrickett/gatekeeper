package com.aegisgatekeeper.app.views

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
fun VaultReviewScreen(overrideTime: LocalTime? = null) {
    val state by GatekeeperStateManager.state.collectAsState()
    var currentTime by remember { mutableStateOf(overrideTime ?: LocalTime.now()) }

    // Only auto-update if we aren't overriding the time for tests
    if (overrideTime == null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000 * 60) // Check every minute
                currentTime = LocalTime.now()
            }
        }
    }

    val isUnlocked = isVaultUnlocked(currentTime)
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
                VaultList(unresolvedItems, Modifier.weight(1f))
            } else {
                LockedVaultMessage(Modifier.weight(1f))
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun VaultList(
    items: List<VaultItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "You have until 6:30 PM to review these.",
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
                    VaultItemCard(it)
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun VaultItemCard(item: VaultItem) {
    TerminalPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
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
    }
}

@Suppress("FunctionName")
@Composable
private fun LockedVaultMessage(modifier: Modifier = Modifier) {
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
            text = "The Vault is Locked",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your distractions are safely stored.\nYou can review them between 6:00 PM and 6:30 PM.",
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
