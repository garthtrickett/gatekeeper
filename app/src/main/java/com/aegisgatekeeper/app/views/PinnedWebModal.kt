package com.aegisgatekeeper.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
actual @Suppress("FunctionName")
@Composable
actual fun PinnedWebModal(
    url: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
            }

            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                filterRules = emptyList(),
                networkBlocklist = emptyList(),
                onPageLoaded = { loadedUrl ->
                    // Once login is successful, we'll be redirected back to youtube.
                    // At that point, we can close this modal.
                    if (loadedUrl.contains("youtube.com") && !loadedUrl.contains("accounts.google.com")) {
                        onClose()
                    }
                },
            )
        }
    }
}
    url: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
            }

                        BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                filterRules = emptyList(),
                networkBlocklist = emptyList(),
                onPageLoaded = { loadedUrl ->
                    // Once login is successful, we'll be redirected back to youtube.
                    // At that point, we can close this modal.
                    if (loadedUrl.contains("youtube.com") && !loadedUrl.contains("accounts.google.com")) {
                        onClose()
                    }
                },
            )
        }
    }
}
