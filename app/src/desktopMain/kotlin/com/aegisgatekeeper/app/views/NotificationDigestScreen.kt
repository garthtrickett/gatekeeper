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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.MessageStatus
import com.aegisgatekeeper.app.domain.ScheduledMessage
import com.aegisgatekeeper.app.domain.isVaultUnlocked
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.UUID

@Suppress("FunctionName")
@Composable
fun NotificationDigestScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime = LocalTime.now()
        }
    }

    // Dynamic check using user-configured phase windows from state
    val isUnlocked =
        isVaultUnlocked(
            currentTime,
            state.gatheringStartMinutes,
            state.gatheringEndMinutes,
        )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("The Digest", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Batched notifications from blacklisted apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (!isUnlocked) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDD14", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "The Moat is Active",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text =
                                "Your notifications are being intercepted and pooled safely.\n" +
                                    "You can review them during the Gathering Phase.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.notificationDigest.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No intercepted notifications. Your focus is pristine.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                IndustrialButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.ClearNotificationDigest) },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Clear All Notifications",
                    isWarning = true,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(state.notificationDigest) { log ->
                        val baseName = log.title.substringAfter(":").trim()
                        val matchedChat =
                            state.beeperChats.firstOrNull {
                                it.name.contains(baseName, ignoreCase = true) || baseName.contains(it.name, ignoreCase = true)
                            }

                        var isReplying by remember { mutableStateOf(false) }
                        var replyText by remember { mutableStateOf("") }
                        var isReplied by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth().let { if (isReplied) it.alpha(0.5f) else it },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    log.packageName.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    log.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.content, style = MaterialTheme.typography.bodyMedium)

                                if (matchedChat != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (isReplying) {
                                        IndustrialTextField(
                                            value = replyText,
                                            onValueChange = { replyText = it },
                                            label = { Text("Reply to ${matchedChat.name}") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = false,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            val delays = listOf("Send Now" to 0L, "+15m" to 15 * 60_000L, "+1h" to 60 * 60_000L)
                                            delays.forEach { (label, delayMillis) ->
                                                IndustrialButton(
                                                    onClick = {
                                                        if (replyText.isNotBlank()) {
                                                            GatekeeperStateManager.dispatch(
                                                                GatekeeperAction.ScheduleMessage(
                                                                    ScheduledMessage(
                                                                        id = UUID.randomUUID().toString(),
                                                                        beeperRoomId = matchedChat.roomId,
                                                                        chatName = matchedChat.name,
                                                                        messageText = replyText.trim(),
                                                                        scheduledTimestamp = System.currentTimeMillis() + delayMillis,
                                                                        status = MessageStatus.PENDING,
                                                                    ),
                                                                ),
                                                            )
                                                            replyText = ""
                                                            isReplying = false
                                                            isReplied = true
                                                        }
                                                    },
                                                    text = label,
                                                    enabled = replyText.isNotBlank(),
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        IndustrialButton(
                                            onClick = { isReplying = false },
                                            text = "Cancel",
                                            isWarning = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        IndustrialButton(
                                            onClick = { isReplying = true },
                                            text = if (isReplied) "Replied ✓" else "Outpost Reply",
                                            enabled = !isReplied,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
