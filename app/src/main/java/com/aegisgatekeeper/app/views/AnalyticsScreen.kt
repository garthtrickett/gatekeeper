package com.aegisgatekeeper.app.views

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun AnalyticsScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    val context = LocalContext.current

    if (!state.isProTier) {
        PaywallScreen(
            title = "Pro Analytics & Export",
            description =
                "Upgrade to Pro to visualize your Intent Success Rate, Time Reclaimed, " +
                    "and export your digital footprint to CSV/Markdown.",
        )
        return
    }

    LaunchedEffect(state.exportData) {
        state.exportData?.let { data ->
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_TEXT, data)
                }
            context.startActivity(Intent.createChooser(intent, "Export Gatekeeper Data"))
            GatekeeperStateManager.dispatch(GatekeeperAction.ClearExportData)
        }
    }

    val totalInterceptions = state.analyticsBypasses + state.analyticsGiveUps
    val successRate =
        if (totalInterceptions > 0) {
            (state.analyticsGiveUps.toFloat() / totalInterceptions) * 100
        } else {
            0f
        }

    val timeReclaimedMinutes = state.analyticsGiveUps * 15

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Insights", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Quantify your digital sovereignty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Intent Success Rate", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${successRate.toInt()}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${state.analyticsGiveUps} Give-Ups vs ${state.analyticsBypasses} Bypasses",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Time Reclaimed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "~$timeReclaimedMinutes mins",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Based on 15 mins saved per Give-Up", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            IndustrialButton(
                onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.GenerateExportData) },
                modifier = Modifier.fillMaxWidth(),
                text = "Export Data (Markdown/CSV)",
            )
        }
    }
}
