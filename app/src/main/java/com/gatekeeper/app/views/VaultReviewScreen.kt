package com.gatekeeper.app.views

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.gatekeeper.app.GatekeeperStateManager
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.domain.VaultItem
import com.gatekeeper.app.domain.isVaultUnlocked
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
    val unresolvedItems = state.vaultItems.filter { !it.isResolved }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isUnlocked) {
            VaultList(unresolvedItems)
        } else {
            LockedVaultMessage()
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun VaultList(items: List<VaultItem>) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(text = "Lookup Vault", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
            Button(
                onClick = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.MarkVaultItemResolved(item.id))
                },
            ) {
                Text(text = "Resolved")
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun LockedVaultMessage() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
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
    }
}
