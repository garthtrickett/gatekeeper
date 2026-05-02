package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.MessageStatus
import com.aegisgatekeeper.app.domain.ScheduledMessage
import java.util.UUID

@Composable
fun OutpostScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Composer, 1 = Queue

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Messaging Outpost", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sniper-shot messaging. Send replies without seeing feeds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IndustrialButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.RequestBeeperSync) },
                    text = "Sync",
                    isLoading = state.isSyncingBeeper
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Composer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Queue (${state.scheduledMessages.count { it.status == MessageStatus.PENDING }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                OutpostComposerView(state.beeperChats)
            } else {
                OutpostQueueView(state.scheduledMessages.filter { it.status == MessageStatus.PENDING })
            }
        }
    }
}

@Composable
private fun OutpostComposerView(chats: List<BeeperChat>) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No chats available. Go to Account settings to connect Beeper, then tap Sync.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<BeeperChat?>(null) }
    var messageText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box {
            IndustrialButton(
                onClick = { expanded = true },
                text = selectedChat?.let { "${it.name} (${it.network ?: "Unknown"})" } ?: "Select Chat",
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                chats.forEach { chat ->
                    DropdownMenuItem(
                        text = { Text("${chat.name} (${chat.network ?: "Unknown"})") },
                        onClick = {
                            selectedChat = chat
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        IndustrialTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Message Body") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            singleLine = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Send Delay", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val delays = listOf(
            "Send Now" to 0L,
            "+15m" to 15 * 60_000L,
            "+1h" to 60 * 60_000L,
            "+Tomorrow" to 24 * 60 * 60_000L
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            delays.forEach { (label, delayMillis) ->
                IndustrialButton(
                    onClick = {
                        if (selectedChat != null && messageText.isNotBlank()) {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.ScheduleMessage(
                                    ScheduledMessage(
                                        id = UUID.randomUUID().toString(),
                                        beeperRoomId = selectedChat!!.roomId,
                                        chatName = selectedChat!!.name,
                                        messageText = messageText.trim(),
                                        scheduledTimestamp = System.currentTimeMillis() + delayMillis,
                                        status = MessageStatus.PENDING
                                    )
                                )
                            )
                            messageText = ""
                        }
                    },
                    text = label,
                    enabled = selectedChat != null && messageText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OutpostQueueView(pendingMessages: List<ScheduledMessage>) {
    if (pendingMessages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Queue is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(pendingMessages, key = { it.id }) { msg ->
            var timeLeft by remember { mutableLongStateOf(msg.scheduledTimestamp - System.currentTimeMillis()) }

            LaunchedEffect(msg.scheduledTimestamp) {
                while (timeLeft > 0) {
                    kotlinx.coroutines.delay(1000)
                    timeLeft = msg.scheduledTimestamp - System.currentTimeMillis()
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(msg.chatName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        val timeDisplay = if (timeLeft > 0) {
                            val hours = timeLeft / 3600_000
                            val minutes = (timeLeft % 3600_000) / 60_000
                            val seconds = (timeLeft % 60_000) / 1000
                            if (hours > 0) {
                                String.format("in %02d:%02d:%02d", hours, minutes, seconds)
                            } else {
                                String.format("in %02d:%02d", minutes, seconds)
                            }
                        } else {
                            "Sending..."
                        }
                        Text(timeDisplay, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg.messageText, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IndustrialButton(
                            onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.CancelScheduledMessage(msg.id)) },
                            text = "Drop",
                            isWarning = true
                        )
                    }
                }
            }
        }
    }
}
