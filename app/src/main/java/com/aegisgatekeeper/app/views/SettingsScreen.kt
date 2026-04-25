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
import com.aegisgatekeeper.app.domain.IndustrialButton
import androidx.compose.runtime.Composable
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
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun SettingsScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }

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

    var dwStart by remember(state.deepWorkStartMinutes) { mutableStateOf(formatTime(state.deepWorkStartMinutes)) }
    var dwEnd by remember(state.deepWorkEndMinutes) { mutableStateOf(formatTime(state.deepWorkEndMinutes)) }
    var gStart by remember(state.gatheringStartMinutes) { mutableStateOf(formatTime(state.gatheringStartMinutes)) }
    var gEnd by remember(state.gatheringEndMinutes) { mutableStateOf(formatTime(state.gatheringEndMinutes)) }

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
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialTextField(
                value = dwEnd,
                onValueChange = { dwEnd = it },
                label = { Text("End HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true
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
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialTextField(
                value = gEnd,
                onValueChange = { gEnd = it },
                label = { Text("End HH:MM") },
                modifier = Modifier.weight(1f),
                singleLine = true
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
                }
            },
            text = "Save Phase Times",
            modifier = Modifier.align(Alignment.End)
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
                value = state.syncServerUrl,
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
