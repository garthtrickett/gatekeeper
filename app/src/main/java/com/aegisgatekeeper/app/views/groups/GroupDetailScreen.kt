package com.aegisgatekeeper.app.views.groups

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.views.groups.CheckInDialog
import com.aegisgatekeeper.app.views.groups.EditAppsDialog
import com.aegisgatekeeper.app.views.groups.RuleChoiceDialog
import com.aegisgatekeeper.app.views.groups.ScheduledBlockDialog
import com.aegisgatekeeper.app.views.groups.TimeLimitDialog

@Suppress("FunctionName")
@Composable
fun GroupDetailScreen(
    group: AppGroup,
    onBack: () -> Unit,
) {
    var showRuleChoice by remember { mutableStateOf(false) }
    var showTimeLimitDialog by remember { mutableStateOf(false) }
    var showScheduledBlockDialog by remember { mutableStateOf(false) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var showEditAppsDialog by remember { mutableStateOf(false) }
    var showDomainBlockDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pm = context.packageManager

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(group.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Configure rules and assigned apps for this group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        IndustrialButton(onClick = onBack, text = "← All Groups")
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Apps in Group:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            IndustrialButton(onClick = { showEditAppsDialog = true }, text = "Edit Apps")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                group.apps
                    .take(5)
                    .map { pkg ->
                        try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) {
                            val parts = pkg.split('.')
                            val name =
                                parts.lastOrNull { it != "com" && it != "android" && it != "app" && it != "org" && it != "net" }
                                    ?: parts.last()
                            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                    }.joinToString(", ") + if (group.apps.size > 5) "..." else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        val domainRule = group.rules.filterIsInstance<BlockingRule.DomainBlock>().firstOrNull()
        val blockedDomains = domainRule?.domains ?: emptySet()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Blocked Domains:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (domainRule != null) {
                    Switch(
                        checked = domainRule.isEnabled,
                        onCheckedChange = { GatekeeperStateManager.dispatch(GatekeeperAction.ToggleRule(domainRule.id, group.id, it)) },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            ),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                IndustrialButton(onClick = { showDomainBlockDialog = true }, text = "Edit Domains")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (blockedDomains.isEmpty()) {
                "No domains blocked."
            } else {
                blockedDomains.take(5).joinToString(", ") + if (blockedDomains.size > 5) "..." else ""
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rule Policy", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = group.combinator == com.aegisgatekeeper.app.domain.RuleCombinator.ANY,
                onClick = {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.UpdateGroupCombinator(group.id, com.aegisgatekeeper.app.domain.RuleCombinator.ANY),
                    )
                },
                label = { Text("BLOCK IF ANY RULE IS ACTIVE (OR)") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
            FilterChip(
                selected = group.combinator == com.aegisgatekeeper.app.domain.RuleCombinator.ALL,
                onClick = {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.UpdateGroupCombinator(group.id, com.aegisgatekeeper.app.domain.RuleCombinator.ALL),
                    )
                },
                label = { Text("BLOCK IF ALL RULES ARE ACTIVE (AND)") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Blocking Rules", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            IndustrialButton(onClick = { showRuleChoice = true }, text = "+ Add Rule")
        }
        Spacer(modifier = Modifier.height(16.dp))

        val displayRules = group.rules.filter { it !is BlockingRule.DomainBlock }
        if (displayRules.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No rules active.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(displayRules) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                when (rule) {
                                    is BlockingRule.TimeLimit -> {
                                        Text("Daily Time Limit", fontWeight = FontWeight.Bold)
                                        Text("${rule.timeLimitMinutes} minutes/day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    is BlockingRule.ScheduledBlock -> {
                                        Text("Scheduled Block", fontWeight = FontWeight.Bold)
                                        rule.timeSlots.forEach { slot ->
                                            val startHour = slot.startTimeMinutes / 60
                                            val startMin = slot.startTimeMinutes % 60
                                            val endHour = slot.endTimeMinutes / 60
                                            val endMin = slot.endTimeMinutes % 60
                                            Text(
                                                String.format("%02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            rule.daysOfWeek.joinToString { it.name.take(3) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    is BlockingRule.CheckIn -> {
                                        Text("Strict Check-In", fontWeight = FontWeight.Bold)
                                        Text(
                                            "${rule.checkInTimesMinutes.size} tokens (${rule.durationMinutes}m each)",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            rule.daysOfWeek.joinToString { it.name.take(3) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    is BlockingRule.DomainBlock -> {
                                        // Rendered separately in the blocked domains section above
                                    }
                                }
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { GatekeeperStateManager.dispatch(GatekeeperAction.ToggleRule(rule.id, group.id, it)) },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                    ),
                            )
                            IndustrialButton(
                                onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.DeleteRule(rule.id, group.id)) },
                                text = "DEL",
                                isWarning = true,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        IndustrialButton(
            onClick = {
                GatekeeperStateManager.dispatch(GatekeeperAction.DeleteAppGroup(group.id))
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            text = "Delete Group",
            isWarning = true,
        )
    }

    if (showRuleChoice) {
        RuleChoiceDialog(
            onDismiss = { showRuleChoice = false },
            onSelectTimeLimit = {
                showRuleChoice = false
                showTimeLimitDialog = true
            },
            onSelectScheduledBlock = {
                showRuleChoice = false
                showScheduledBlockDialog = true
            },
            onSelectCheckIn = {
                showRuleChoice = false
                showCheckInDialog = true
            }
        )
    }

    if (showEditAppsDialog) {
        EditAppsDialog(group = group, onDismiss = { showEditAppsDialog = false })
    }

    if (showCheckInDialog) {
        CheckInDialog(group = group, onDismiss = { showCheckInDialog = false })
    }

    if (showTimeLimitDialog) {
        TimeLimitDialog(group = group, onDismiss = { showTimeLimitDialog = false })
    }

    if (showScheduledBlockDialog) {
        ScheduledBlockDialog(group = group, onDismiss = { showScheduledBlockDialog = false })
    }

    if (showDomainBlockDialog) {
        DomainBlockDialog(group = group, onDismiss = { showDomainBlockDialog = false })
    }
}
