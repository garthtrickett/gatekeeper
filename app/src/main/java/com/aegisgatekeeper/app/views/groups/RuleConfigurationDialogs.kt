package com.aegisgatekeeper.app.views.groups

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.DayOfWeek
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.domain.TimeSlot
import java.util.UUID

@Suppress("FunctionName")
@Composable
fun RuleChoiceDialog(
    onDismiss: () -> Unit,
    onSelectTimeLimit: () -> Unit,
    onSelectScheduledBlock: () -> Unit,
    onSelectCheckIn: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Select Rule Type", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialButton(
                    onClick = onSelectTimeLimit,
                    modifier = Modifier.fillMaxWidth(),
                    text = "Daily Time Limit",
                )
                Spacer(modifier = Modifier.height(8.dp))
                IndustrialButton(
                    onClick = onSelectScheduledBlock,
                    modifier = Modifier.fillMaxWidth(),
                    text = "Scheduled Block",
                )
                Spacer(modifier = Modifier.height(8.dp))
                IndustrialButton(
                    onClick = onSelectCheckIn,
                    modifier = Modifier.fillMaxWidth(),
                    text = "Strict Check-In",
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun DomainBlockDialog(
    group: AppGroup,
    onDismiss: () -> Unit,
) {
    val existingRule = group.rules.filterIsInstance<com.aegisgatekeeper.app.domain.BlockingRule.DomainBlock>().firstOrNull()
    var domains by remember { mutableStateOf(existingRule?.domains ?: emptySet<String>()) }
    var currentInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    val vpnLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                if (existingRule != null) {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.UpdateDomainBlockRule(
                            ruleId = existingRule.id,
                            groupId = group.id,
                            domains = domains,
                        ),
                    )
                } else {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.AddDomainBlockRule(
                            id = UUID.randomUUID().toString(),
                            groupId = group.id,
                            domains = domains,
                        ),
                    )
                }
                onDismiss()
            }
        }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp).fillMaxWidth().height(500.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Domain Block", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IndustrialTextField(
                        value = currentInput,
                        onValueChange = { currentInput = it },
                        label = { Text("e.g. reddit.com") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            val d = currentInput.trim().lowercase()
                            if (d.isNotEmpty()) {
                                domains = domains + d
                                currentInput = ""
                            }
                        },
                        text = "Add",
                        enabled = currentInput.isNotBlank(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(domains.toList()) { domain ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(domain)
                            Text(
                                "\uD83D\uDDD1\uFE0F",
                                modifier =
                                    Modifier.clickable {
                                        domains = domains - domain
                                    },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            val intent = android.net.VpnService.prepare(context)
                            if (intent != null) {
                                // Requires user permission dialog
                                vpnLauncher.launch(intent)
                            } else {
                                // Already has permission
                                if (existingRule != null) {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.UpdateDomainBlockRule(
                                            ruleId = existingRule.id,
                                            groupId = group.id,
                                            domains = domains,
                                        ),
                                    )
                                } else {
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.AddDomainBlockRule(
                                            id = UUID.randomUUID().toString(),
                                            groupId = group.id,
                                            domains = domains,
                                        ),
                                    )
                                }
                                onDismiss()
                            }
                        },
                        text = "Save",
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun EditAppsDialog(
    group: AppGroup,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var selectedApps by remember { mutableStateOf(group.apps) }
    var searchQuery by remember { mutableStateOf("") }

    val installedApps =
        remember {
            pm
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        }

    val filteredApps =
        remember(searchQuery, installedApps, selectedApps) {
            val apps =
                if (searchQuery.isBlank()) {
                    installedApps
                } else {
                    installedApps.filter {
                        pm.getApplicationLabel(it).toString().contains(searchQuery, ignoreCase = true)
                    }
                }
            apps.sortedWith(
                compareBy<android.content.pm.ApplicationInfo> { it.packageName !in selectedApps }
                    .thenBy { pm.getApplicationLabel(it).toString() },
            )
        }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp).fillMaxWidth().height(500.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Edit Apps in ${group.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                IndustrialTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps =
                                            if (selectedApps.contains(app.packageName)) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                    }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedApps.contains(app.packageName),
                                onCheckedChange = null,
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    ),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = pm.getApplicationLabel(app).toString().uppercase(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (selectedApps.contains(app.packageName)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(GatekeeperAction.UpdateGroupApps(group.id, selectedApps))
                            onDismiss()
                        },
                        enabled = selectedApps.isNotEmpty(),
                        text = "Save",
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun CheckInDialog(
    group: AppGroup,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf("15") }

    data class UiTime(
        val hour: String = "10",
        val min: String = "30",
    )
    var times by remember { mutableStateOf(listOf(UiTime())) }
    var selectedDays by remember { mutableStateOf(DayOfWeek.values().toSet()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Strict Check-In", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Duration per Check-In (minutes)")
                IndustrialTextField(value = duration, onValueChange = {
                    if (it.all { char ->
                            char.isDigit()
                        }
                    ) {
                        duration = it
                    }
                }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))

                Text("Allowed Times (HH:MM 24h)")
                times.forEachIndexed { index, time ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        IndustrialTextField(value = time.hour, onValueChange = { h ->
                            times =
                                times.mapIndexed { i, t -> if (i == index) t.copy(hour = h) else t }
                        }, modifier = Modifier.weight(1f))
                        Text(":", modifier = Modifier.padding(horizontal = 8.dp))
                        IndustrialTextField(value = time.min, onValueChange = { m ->
                            times =
                                times.mapIndexed { i, t -> if (i == index) t.copy(min = m) else t }
                        }, modifier = Modifier.weight(1f))
                        if (times.size > 1) {
                            Text(
                                "🗑️",
                                modifier =
                                    Modifier.padding(start = 8.dp).clickable {
                                        times =
                                            times.filterIndexed { i, _ -> i != index }
                                    },
                            )
                        }
                    }
                }
                IndustrialButton(onClick = { times = times + UiTime() }, text = "+ Add Time")

                Spacer(modifier = Modifier.height(16.dp))
                Text("Active Days")
                Column {
                    val chipColors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DayOfWeek.values().take(4).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = { selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day },
                                label = { Text(day.name.take(3)) },
                                colors = chipColors,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DayOfWeek.values().drop(4).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = { selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day },
                                label = { Text(day.name.take(3)) },
                                colors = chipColors,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            val domainTimes =
                                times.map {
                                    (it.hour.toIntOrNull() ?: 10) * 60 + (it.min.toIntOrNull() ?: 0)
                                }
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.AddCheckInRule(
                                    id =
                                        java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                    groupId = group.id,
                                    checkInTimesMinutes = domainTimes,
                                    durationMinutes = duration.toIntOrNull() ?: 15,
                                    daysOfWeek = selectedDays,
                                ),
                            )
                            onDismiss()
                        },
                        text = "Save",
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun TimeLimitDialog(
    group: AppGroup,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf("60") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Daily Time Limit", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialTextField(
                    value = minutes,
                    onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
                    label = { Text("Minutes per day") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            val limit = minutes.toIntOrNull() ?: 60
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.AddTimeLimitRule(UUID.randomUUID().toString(), group.id, limit),
                            )
                            onDismiss()
                        },
                        text = "Save",
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun ScheduledBlockDialog(
    group: AppGroup,
    onDismiss: () -> Unit,
) {
    data class UiTimeSlot(
        val startHour: String = "09",
        val startMin: String = "00",
        val endHour: String = "17",
        val endMin: String = "00",
    )
    var timeSlots by remember { mutableStateOf(listOf(UiTimeSlot())) }
    var selectedDays by remember { mutableStateOf(DayOfWeek.values().toSet()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Scheduled Block", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                timeSlots.forEachIndexed { index, slot ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Slot ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (timeSlots.size > 1) {
                            Text(
                                "🗑️",
                                modifier =
                                    Modifier.padding(start = 8.dp).clickable {
                                        timeSlots =
                                            timeSlots.filterIndexed { i, _ -> i != index }
                                    },
                            )
                        }
                    }

                    Text("Start Time (HH:MM 24h)")
                    Row {
                        IndustrialTextField(value = slot.startHour, onValueChange = { h ->
                            timeSlots =
                                timeSlots.mapIndexed { i, s ->
                                    if (i ==
                                        index
                                    ) {
                                        s.copy(startHour = h)
                                    } else {
                                        s
                                    }
                                }
                        }, modifier = Modifier.weight(1f))
                        Text(":", modifier = Modifier.padding(horizontal = 8.dp).align(Alignment.CenterVertically))
                        IndustrialTextField(value = slot.startMin, onValueChange = { m ->
                            timeSlots =
                                timeSlots.mapIndexed { i, s ->
                                    if (i ==
                                        index
                                    ) {
                                        s.copy(startMin = m)
                                    } else {
                                        s
                                    }
                                }
                        }, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("End Time (HH:MM 24h)")
                    Row {
                        IndustrialTextField(value = slot.endHour, onValueChange = { h ->
                            timeSlots =
                                timeSlots.mapIndexed { i, s ->
                                    if (i ==
                                        index
                                    ) {
                                        s.copy(endHour = h)
                                    } else {
                                        s
                                    }
                                }
                        }, modifier = Modifier.weight(1f))
                        Text(":", modifier = Modifier.padding(horizontal = 8.dp).align(Alignment.CenterVertically))
                        IndustrialTextField(value = slot.endMin, onValueChange = { m ->
                            timeSlots =
                                timeSlots.mapIndexed { i, s ->
                                    if (i ==
                                        index
                                    ) {
                                        s.copy(endMin = m)
                                    } else {
                                        s
                                    }
                                }
                        }, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                IndustrialButton(onClick = { timeSlots = timeSlots + UiTimeSlot() }, text = "+ Add Time Slot")

                Spacer(modifier = Modifier.height(16.dp))

                Text("Active Days")
                // Simple flow layout for days
                Column {
                    val chipColors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DayOfWeek.values().take(4).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = {
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                                },
                                label = { Text(day.name.take(3)) },
                                colors = chipColors,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DayOfWeek.values().drop(4).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = {
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                                },
                                label = { Text(day.name.take(3)) },
                                colors = chipColors,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = {
                            val domainSlots =
                                timeSlots.map {
                                    val stMinutes = (it.startHour.toIntOrNull() ?: 9) * 60 + (it.startMin.toIntOrNull() ?: 0)
                                    val etMinutes = (it.endHour.toIntOrNull() ?: 17) * 60 + (it.endMin.toIntOrNull() ?: 0)
                                    com.aegisgatekeeper.app.domain
                                        .TimeSlot(stMinutes, etMinutes)
                                }
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.AddScheduledBlockRule(
                                    id = UUID.randomUUID().toString(),
                                    groupId = group.id,
                                    timeSlots = domainSlots,
                                    daysOfWeek = selectedDays,
                                ),
                            )
                            onDismiss()
                        },
                        text = "Save",
                    )
                }
            }
        }
    }
}
