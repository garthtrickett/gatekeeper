package com.aegisgatekeeper.app.views.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.DayOfWeek
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.TemporaryWhitelist
import com.aegisgatekeeper.app.views.TerminalPanel
import kotlinx.coroutines.delay

@Suppress("FunctionName")
@Composable
fun AppGroupsListScreen(
    state: GatekeeperState,
    onGroupSelected: (AppGroup) -> Unit,
    onAddGroupClick: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("App Groups", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Group apps and apply blocking rules.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Manual Lockdown Toggle
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = if (state.isManualLockdownActive) Color(0xFF93000A) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (state.isManualLockdownActive) Color.White else MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.isManualLockdownActive) "LOCKDOWN ENGAGED" else "MANUAL LOCKDOWN",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Overrides all schedules. Blocks all apps in groups.",
                        textAlign = TextAlign.Center,
                        color =
                            if (state.isManualLockdownActive) {
                                Color.White.copy(
                                    alpha = 0.8f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    IndustrialButton(
                        onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.SetManualLockdown(!state.isManualLockdownActive)) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        text = if (state.isManualLockdownActive) "DISENGAGE" else "ENGAGE LOCKDOWN",
                        isWarning = !state.isManualLockdownActive,
                    )
                }
            }

            if (state.activeWhitelists.isNotEmpty()) {
                ShieldStatusCard(state.activeWhitelists)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Global Settings
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Global Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Friction Type", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.activeFrictionGame == com.aegisgatekeeper.app.domain.FrictionGame.HOLD_STEADY,
                            onClick = {
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.SetFrictionGame(com.aegisgatekeeper.app.domain.FrictionGame.HOLD_STEADY),
                                )
                            },
                            label = { Text("Hold Steady") },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                        FilterChip(
                            selected = state.activeFrictionGame == com.aegisgatekeeper.app.domain.FrictionGame.GAUNTLET,
                            onClick = {
                                GatekeeperStateManager.dispatch(
                                    GatekeeperAction.SetFrictionGame(com.aegisgatekeeper.app.domain.FrictionGame.GAUNTLET),
                                )
                            },
                            label = { Text("The Gauntlet") },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                    }
                }
            }

            if (state.appGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No groups configured. Create one to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.appGroups) { group ->
                        TerminalPanel(modifier = Modifier.fillMaxWidth().clickable { onGroupSelected(group) }) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = group.name.uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "[${group.apps.size} APPS]",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ">> ${group.rules.size} ACTIVE BLOCKING RULES",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                )

                                val checkInRule =
                                    group.rules
                                        .filterIsInstance<com.aegisgatekeeper.app.domain.BlockingRule.CheckIn>()
                                        .firstOrNull { it.isEnabled }
                                if (checkInRule != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Check-In Tokens", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CheckInTokensRow(group = group, rule = checkInRule, state = state)
                                }
                            }
                        }
                    }
                }
            }
        }
        IndustrialButton(
            onClick = onAddGroupClick,
            text = "+",
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Suppress("FunctionName")
@Composable
fun ShieldStatusCard(whitelists: Map<String, TemporaryWhitelist>) {
    val context = LocalContext.current
    val pm = context.packageManager

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF2E1A1A), // Deep subtle red
                contentColor = Color(0xFFFFB4AB), // Warning peach
            ),
        border = BorderStroke(1.dp, Color(0xFF93000A)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "MOAT BREACHED",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            for (whitelist in whitelists.values) {
                val appName =
                    remember(whitelist.packageName) {
                        try {
                            pm.getApplicationLabel(pm.getApplicationInfo(whitelist.packageName, 0)).toString()
                        } catch (e: Exception) {
                            val parts = whitelist.packageName.split('.')
                            val name =
                                parts.lastOrNull { it != "com" && it != "android" && it != "app" && it != "org" && it != "net" }
                                    ?: parts.last()
                            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                    }

                var remainingSeconds by remember(whitelist.expiresAtTimestamp) {
                    mutableStateOf((whitelist.expiresAtTimestamp - System.currentTimeMillis()) / 1000)
                }

                LaunchedEffect(whitelist.expiresAtTimestamp) {
                    while (remainingSeconds > 0) {
                        delay(1000)
                        remainingSeconds = (whitelist.expiresAtTimestamp - System.currentTimeMillis()) / 1000
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (whitelist.reason == null) "Task Completed" else "Emergency: ${whitelist.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB4AB).copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        String.format("%02d:%02d", (remainingSeconds.coerceAtLeast(0)) / 60, (remainingSeconds.coerceAtLeast(0)) % 60),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            IndustrialButton(
                onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.ReengageShields) },
                modifier = Modifier.fillMaxWidth(),
                text = "Raise Moat Now",
                isWarning = true,
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun CheckInTokensRow(
    group: AppGroup,
    rule: BlockingRule.CheckIn,
    state: GatekeeperState,
) {
    val isGroupActive = group.apps.any { state.activeWhitelists.containsKey(it) }
    if (isGroupActive) {
        IndustrialButton(
            onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.EndGroupSession(group.id)) },
            text = "Mark Done (Close Gate)",
            isWarning = true,
        )
        return
    }

    val calendar = java.util.Calendar.getInstance()
    val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    val currentDay =
        when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> DayOfWeek.MONDAY
            java.util.Calendar.TUESDAY -> DayOfWeek.TUESDAY
            java.util.Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            java.util.Calendar.THURSDAY -> DayOfWeek.THURSDAY
            java.util.Calendar.FRIDAY -> DayOfWeek.FRIDAY
            java.util.Calendar.SATURDAY -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }

    if (!rule.daysOfWeek.contains(currentDay)) {
        Text("No check-ins scheduled for today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis

    val consumedToday = state.consumedCheckIns.filter { it.groupId == group.id && it.timestamp >= startOfDay }
    val consumedTimes = consumedToday.map { it.timeMinutes }

    var showAccountabilityForTime by remember { mutableStateOf<Int?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        rule.checkInTimesMinutes.sorted().forEach { time ->
            val isConsumed = consumedTimes.contains(time)
            val isAvailable = !isConsumed && currentMinutes >= time
            val label = String.format("%02d:%02d", time / 60, time % 60)

            FilterChip(
                selected = isConsumed,
                onClick = {
                    if (isConsumed) return@FilterChip
                    if (isAvailable) {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.RedeemCheckInToken(group.id, time, rule.durationMinutes, null, System.currentTimeMillis()),
                        )
                    } else {
                        showAccountabilityForTime = time
                    }
                },
                label = { Text(if (isConsumed) "$label (Used)" else label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor =
                            if (isAvailable) {
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.4f,
                                )
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        labelColor = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                border = BorderStroke(1.dp, if (isAvailable) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
        }
        IndustrialButton(onClick = { showAccountabilityForTime = -1 }, text = "+ Unscheduled")
    }

    if (showAccountabilityForTime != null) {
        var reason by remember { mutableStateOf("") }
        val time = showAccountabilityForTime!!
        Dialog(onDismissRequest = { showAccountabilityForTime = null }) {
            Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (time == -1) "Unscheduled Check-In" else "Early Check-In", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This token is not yet available. Why do you need access now?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    IndustrialTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Accountability Reason") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IndustrialButton(onClick = { showAccountabilityForTime = null }, text = "Cancel", isWarning = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        IndustrialButton(
                            onClick = {
                                if (reason.trim().isNotEmpty()) {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.RedeemCheckInToken(
                                            group.id,
                                            time,
                                            rule.durationMinutes,
                                            reason.trim(),
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                    showAccountabilityForTime = null
                                }
                            },
                            enabled = reason.trim().isNotEmpty(),
                            text = "Redeem",
                        )
                    }
                }
            }
        }
    }
}
