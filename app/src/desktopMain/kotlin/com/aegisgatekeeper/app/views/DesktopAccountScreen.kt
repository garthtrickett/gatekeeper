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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun AccountScreen() {
    val state by GatekeeperStateManager.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (state.isAuthenticated) {
                    AuthenticatedView(onLogout = { GatekeeperStateManager.dispatch(GatekeeperAction.Logout) })
                } else {
                    LoginView()
                }
            }
            SettingsScreen()
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun LoginView() {
    var email by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (submitted) {
            Text("Check your inbox!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "We've sent a magic link to $email. Click it to sign in.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Sovereign Sync", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to sync your data securely across devices.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            IndustrialTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Enter your email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            IndustrialButton(
                onClick = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.RequestMagicLink(email))
                    submitted = true
                },
                enabled = email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                text = "Send Magic Link",
            )

            // --- LOCAL DEV BYPASS ---
            Spacer(Modifier.height(48.dp))
            var devToken by remember { mutableStateOf("") }
            IndustrialTextField(
                value = devToken,
                onValueChange = { devToken = it },
                label = { Text("Local Dev: Paste JWT Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            IndustrialButton(
                onClick = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.LoginSuccess(devToken))
                },
                enabled = devToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                text = "Force Login",
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun AuthenticatedView(onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sync is Active", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your data is being synced securely.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        IndustrialButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), text = "Logout", isWarning = true)
    }
}

@Suppress("FunctionName")
@Composable
fun SettingsScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
