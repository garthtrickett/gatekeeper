package com.gatekeeper.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gatekeeper.app.domain.GatekeeperAction

class VaultCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                VaultCaptureDialog(
                    onDismiss = { finish() },
                    onSave = { query ->
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SaveToVault(
                                query = query,
                                currentTimestamp = System.currentTimeMillis(),
                            ),
                        )
                        Toast.makeText(this, "Saved. We'll look this up at 6 PM.", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun VaultCaptureDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            // A Dialog creates a new sub-window. We must wait a moment for it to be fully
            // attached before requesting focus, otherwise it crashes (especially in UI tests).
            kotlinx.coroutines.delay(50)
            try {
                focusRequester.requestFocus()
            } catch (ignore: IllegalStateException) {
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Lookup Vault", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("What do you want to search?") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                val trimmed = query.trim()
                                if (trimmed.isNotEmpty()) onSave(trimmed)
                            },
                        ),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = query.trim()
                            if (trimmed.isNotEmpty()) onSave(trimmed)
                        },
                        enabled = query.trim().isNotEmpty(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
