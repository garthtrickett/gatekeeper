package com.aegisgatekeeper.app.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun AlternativeActivitiesScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var newActivityDescription by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Positive Habits", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Define alternative activities to suggest when you hit the moat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IndustrialTextField(
                    value = newActivityDescription,
                    onValueChange = { newActivityDescription = it },
                    label = { Text("e.g. Read a book") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IndustrialButton(
                    onClick = {
                        if (newActivityDescription.isNotBlank()) {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.AddAlternativeActivity(
                                    description = newActivityDescription.trim(),
                                    currentTimestamp = System.currentTimeMillis()
                                )
                            )
                            newActivityDescription = ""
                        }
                    },
                    enabled = newActivityDescription.isNotBlank(),
                    text = "Add",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state.alternativeActivities.isEmpty()) {
                Text(
                    "No habits configured yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(state.alternativeActivities) { activity ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = activity.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "🗑️",
                                    modifier = Modifier.clickable {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.RemoveAlternativeActivity(activity.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
