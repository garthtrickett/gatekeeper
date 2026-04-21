package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField

@Suppress("FunctionName")
@Composable
fun SurgicalWebScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var urlInput by remember(state.currentSurgicalUrl) {
        mutableStateOf(state.currentSurgicalUrl ?: "https://google.com")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            IndustrialTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("Surgical URL") },
                singleLine = true,
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                    ),
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onGo = {
                            val finalUrl =
                                if (urlInput.isNotBlank() && !urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                                    "https://$urlInput"
                                } else {
                                    urlInput
                                }
                            GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(finalUrl))
                        },
                    ),
            )
            IndustrialButton(
                onClick = {
                    val finalUrl =
                        if (urlInput.isNotBlank() && !urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                            "https://$urlInput"
                        } else {
                            urlInput
                        }
                    GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(finalUrl))
                },
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                text = "Go",
            )
        }

        val networkBlocklist =
            listOf(
                "google-analytics.com",
                "doubleclick.net",
                "connect.facebook.net",
                "ads.twitter.com",
                "googletagmanager.com",
            )

        BaseSurgicalWebView(
            url = state.currentSurgicalUrl ?: "https://google.com",
            modifier = Modifier.weight(1f),
            cssInjector = { currentUrl ->
                when {
                    currentUrl.contains("twitter.com") || currentUrl.contains("x.com") -> {
                        "[data-testid='sidebarColumn'], " +
                            "[data-testid='primaryColumn'] > div > div:nth-child(2), " +
                            "nav[aria-label='Primary'] > a:nth-child(2), " +
                            "nav[aria-label='Primary'] > a:nth-child(5) { display: none !important; }"
                    }

                    currentUrl.contains("substack.com") -> {
                        ".feed-container, .top-posts-container, .sidebar { display: none !important; }"
                    }

                    currentUrl.contains("youtube.com") -> {
                        "#secondary, #related, ytd-reel-shelf-renderer, ytd-shorts { display: none !important; }"
                    }

                    else -> {
                        ""
                    }
                }
            },
            networkBlocklist = networkBlocklist,
        )
    }
}
