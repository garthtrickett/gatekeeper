package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.network.CefRequest

@Suppress("FunctionName")
@Composable
fun SurgicalWebScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var urlInput by remember { mutableStateOf(state.currentSurgicalUrl ?: "https://google.com") }

    var client by remember { mutableStateOf<dev.datlag.kcef.KCEFClient?>(null) }
    LaunchedEffect(Unit) {
        if (client == null) {
            client = KCEF.newClient()
        }
    }

    // Sync the local input field if state changes externally
    LaunchedEffect(state.currentSurgicalUrl) {
        state.currentSurgicalUrl?.let { urlInput = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            IndustrialTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("Surgical URL") },
                singleLine = true,
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
                text = "Open",
            )
        }

        client?.let { kcefClient ->
            val browser =
                remember(kcefClient) {
                    val b =
                        kcefClient.createBrowser(
                            state.currentSurgicalUrl ?: "https://google.com",
                            org.cef.browser.CefRendering.OFFSCREEN,
                            false,
                        )
                    applySurgicalFilters(b)
                    setupNetworkInterception(b)
                    b
                }

            LaunchedEffect(state.currentSurgicalUrl) {
                state.currentSurgicalUrl?.let { url ->
                    browser.loadURL(url)
                }
            }

            androidx.compose.ui.awt.SwingPanel(
                factory = {
                    browser.uiComponent
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Simulates Chrome Extension filtering by injecting CSS into the page
 * to hide distracting algorithmic elements.
 */
private fun applySurgicalFilters(browser: CefBrowser) {
    browser.client.addLoadHandler(
        object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading) {
                    val currentUrl = browser?.url ?: ""

                    // Define domain-specific surgical masks
                    val cssInjection =
                        when {
                            currentUrl.contains("twitter.com") || currentUrl.contains("x.com") -> {
                                """
                        [data-testid='sidebarColumn'], 
[data-testid='primaryColumn'] > div > div:nth-child(2), 
                        nav[aria-label='Primary'] > a:nth-child(2), 
                        nav[aria-label='Primary'] > a:nth-child(5) { display: none !important; }
                    """
                            }

                            currentUrl.contains("substack.com") -> {
                                """
                        .feed-container, .top-posts-container, .sidebar { display: none !important; }
                    """
                            }

                            currentUrl.contains("youtube.com") -> {
                                """
                        #secondary, #related, ytd-reel-shelf-renderer, ytd-shorts { display: none !important; }
                    """
                            }

                            else -> {
                                ""
                            }
                        }

                    if (cssInjection.isNotBlank()) {
                        val cleanCss = cssInjection.replace("\n", " ").replace("\"", "\\\"")
                        browser?.executeJavaScript(
                            """
                            (function() {
                                var style = document.createElement('style');
                                style.innerHTML = "$cleanCss";
                                document.head.appendChild(style);
                            })();
                            """.trimIndent(),
                            "",
                            0,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Simulates an ad/tracker blocker by intercepting network requests at the CefClient level.
 */
private fun setupNetworkInterception(browser: CefBrowser) {
    val blocklist =
        listOf(
            "google-analytics.com",
            "doubleclick.net",
            "connect.facebook.net",
            "ads.twitter.com",
            "googletagmanager.com",
        )

    browser.client.addRequestHandler(
        object : CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: org.cef.misc.BoolRef?,
            ): CefResourceRequestHandler {
                return object : CefResourceRequestHandlerAdapter() {
                    override fun onBeforeResourceLoad(
                        browser: CefBrowser?,
                        frame: org.cef.browser.CefFrame?,
                        request: CefRequest?,
                    ): Boolean {
                        val url = request?.url ?: return false
                        val shouldBlock = blocklist.any { url.contains(it, ignoreCase = true) }

                        if (shouldBlock) {
                            println("\uD83D\uDEE1\uFE0F Surgical Web: Blocked network request to $url")
                            return true // true cancels the request
                        }

                        return false // false allows the request
                    }
                }
            }
        },
    )
}
