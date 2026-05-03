package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import kotlinx.coroutines.delay

@Suppress("FunctionName")
@Composable
fun SettingsScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    if (showSaveConfirmation) {
        LaunchedEffect(showSaveConfirmation) {
            delay(2000L)
            showSaveConfirmation = false
        }
    }

    fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }

    fun parseTime(timeStr: String): Int? {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                return h * 60 + m
            }
        }
        return null
    }

        var dwStart by remember(state.data.deepWorkStartMinutes) { mutableStateOf(formatTime(state.data.deepWorkStartMinutes)) }
    var dwEnd by remember(state.data.deepWorkEndMinutes) { mutableStateOf(formatTime(state.data.deepWorkEndMinutes)) }
    var gStart by remember(state.data.gatheringStartMinutes) { mutableStateOf(formatTime(state.data.gatheringStartMinutes)) }
    var gEnd by remember(state.data.gatheringEndMinutes) { mutableStateOf(formatTime(state.data.gatheringEndMinutes)) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Phase Boundaries", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Text("Focus Phase (Deep Work)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            IndustrialTextField(
                value = dwStart,
                onValueChange = { dwStart = it },
                label = { Text("Start HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialTextField(
                value = dwEnd,
                onValueChange = { dwEnd = it },
                label = { Text("End HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Gathering Phase (Discovery)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            IndustrialTextField(
                value = gStart,
                onValueChange = { gStart = it },
                label = { Text("Start HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialTextField(
                value = gEnd,
                onValueChange = { gEnd = it },
                label = { Text("End HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        IndustrialButton(
            onClick = {
                val pDwStart = parseTime(dwStart)
                val pDwEnd = parseTime(dwEnd)
                val pGStart = parseTime(gStart)
                val pGEnd = parseTime(gEnd)

                if (pDwStart != null && pDwEnd != null && pGStart != null && pGEnd != null) {
                    GatekeeperStateManager.dispatch(GatekeeperAction.UpdatePhaseWindows(pDwStart, pDwEnd, pGStart, pGEnd))
                    showSaveConfirmation = true
                }
            },
            text = if (showSaveConfirmation) "SAVED ✓" else "Save Phase Times",
            enabled = !showSaveConfirmation,
            invertEnabledColor = showSaveConfirmation,
            modifier = Modifier.align(Alignment.End),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Integrations", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        val context = androidx.compose.ui.platform.LocalContext.current
        val permissionLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                contract =
                    androidx.activity.result.contract.ActivityResultContracts
                        .RequestMultiplePermissions(),
            ) { permissions ->
                val granted = permissions.entries.all { it.value }
                if (granted) {
                    GatekeeperStateManager.dispatch(GatekeeperAction.RequestBeeperSync)
                    android.widget.Toast
                        .makeText(context, "Beeper connected and synced!", android.widget.Toast.LENGTH_SHORT)
                        .show()
                } else {
                    android.widget.Toast
                        .makeText(context, "Beeper permission denied.", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }

                IndustrialButton(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        "com.beeper.android.permission.READ_PERMISSION",
                        "com.beeper.android.permission.SEND_PERMISSION",
                    ),
                )
            },
            text = if (state.sync.beeperChats.isNotEmpty()) "Beeper Connected ✓ (Resync)" else "Connect Beeper",
            isLoading = state.sync.isSyncingBeeper,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Required to send sniper-shot messages via the Outpost.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Advanced Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showAdvanced,
                onCheckedChange = { showAdvanced = it },
            )
        }

        if (showAdvanced) {
            Spacer(modifier = Modifier.height(16.dp))
                        IndustrialTextField(
                value = state.sync.syncServerUrl,
                onValueChange = { GatekeeperStateManager.dispatch(GatekeeperAction.UpdateSyncUrl(it)) },
                label = { Text("Custom Sync Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Override the default sovereign sync server with your own self-hosted instance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
