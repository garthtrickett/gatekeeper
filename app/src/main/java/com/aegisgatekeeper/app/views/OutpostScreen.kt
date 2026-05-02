package com.aegisgatekeeper.app.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.MessageStatus
import com.aegisgatekeeper.app.domain.ScheduledMessage
import kotlinx.coroutines.delay
import java.util.UUID

@Suppress("FunctionName")
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
                    isLoading = state.isSyncingBeeper,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Composer") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Queue (${state.scheduledMessages.count { it.status == MessageStatus.PENDING }})") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
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

@Suppress("FunctionName")
@Composable
private fun OutpostComposerView(chats: List<BeeperChat>) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No chats available. Go to Account settings to connect Beeper, then tap Sync.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<BeeperChat?>(null) }
    var messageText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChats = remember(searchQuery, chats) {
        if (searchQuery.isBlank()) {
            chats
        } else {
            chats.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.network?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().zIndex(1f)) {
            IndustrialTextField(
                value = if (selectedChat != null && !expanded) "${selectedChat!!.name} (${selectedChat!!.network ?: "Unknown"})" else searchQuery,
                onValueChange = {
                    searchQuery = it
                    selectedChat = null
                    expanded = true
                },
                label = { Text("Select Chat") },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) expanded = true
                    },
                singleLine = true,
            )

                        if (expanded) {
                Card(
                    modifier = Modifier
                        .padding(top = 64.dp)
                        .fillMaxWidth()
                        .heightIn(max = 250.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (filteredChats.isEmpty()) {
                            item {
                                Text("No chats found", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(filteredChats.take(20)) { chat ->
                                Text(
                                    text = "${chat.name} (${chat.network ?: "Unknown"})",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedChat = chat
                                            searchQuery = "${chat.name} (${chat.network ?: "Unknown"})"
                                            expanded = false
                                            focusManager.clearFocus()
                                        }
                                        .padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        IndustrialTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Message Body") },
            modifier = Modifier.fillMaxWidth().height(150.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) expanded = false
                },
            singleLine = false,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Send Delay", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val delays =
            listOf(
                "Send Now" to 0L,
                "+15m" to 15 * 60_000L,
                "+1h" to 60 * 60_000L,
                "+Tomorrow" to 24 * 60 * 60_000L,
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
                                        status = MessageStatus.PENDING,
                                    ),
                                ),
                            )
                            messageText = ""
                        }
                    },
                    text = label,
                    enabled = selectedChat != null && messageText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Suppress("FunctionName")
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
                    delay(1000)
                    timeLeft = msg.scheduledTimestamp - System.currentTimeMillis()
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(msg.chatName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        val timeDisplay =
                            if (timeLeft > 0) {
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
                    Text(
                        msg.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IndustrialButton(
                            onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.CancelScheduledMessage(msg.id)) },
                            text = "Drop",
                            isWarning = true,
                        )
                    }
                }
            }
        }
    }
}
