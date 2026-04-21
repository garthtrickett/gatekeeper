package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun SurgicalFacebookScreen(
    url: String,
    onClose: () -> Unit,
) {
    val cookieManager = remember { CookieManager.getInstance() }

    // In production, we check cookies. In UI tests, we can force this via state injection if needed,
    // but the most robust way is to inject a dummy cookie in the @Before setup of the test.
    var isLoggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cookies = cookieManager.getCookie("https://m.facebook.com") ?: ""
        isLoggedIn = cookies.contains("c_user=") || cookies.contains("xs=")
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        // Pushes the header below the system status bar
    ) {
        // Header Navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IndustrialButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/")) },
                    text = "Groups",
                    enabled = !url.contains("/groups/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/events/")) },
                    text = "Events",
                    enabled = !url.contains("/events/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = { GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/search/")) },
                    text = "Search",
                    enabled = !url.contains("/search/"),
                    invertEnabledColor = true,
                )
                if (isLoggedIn) {
                    IndustrialButton(
                        onClick = {
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()
                            isLoggedIn = false
                        },
                        text = "Logout",
                        isWarning = true,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
        }

        if (isLoggedIn) {
            SurgicalWebView(
                url = url,
                onLogout = {
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                    isLoggedIn = false
                },
            )
        } else {
            LoginWebView(onLoginSuccess = { isLoggedIn = true })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
private fun ColumnScope.LoginWebView(onLoginSuccess: () -> Unit) {
    val cookieManager = remember { CookieManager.getInstance() }

    AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        factory = {
            WebView(it).apply {
                layoutParams =
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            android.util.Log.d("Gatekeeper", "📡 FB-LOGIN-LOADING: $url")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.e("Gatekeeper", "❌ FB-LOGIN-ERROR: ${error?.description} at ${request?.url}")
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            android.util.Log.d("Gatekeeper", "✅ FB-LOGIN-LOADED: $url")
                            val cookies = cookieManager.getCookie("https://m.facebook.com") ?: ""
                            if (cookies.contains("c_user=") || cookies.contains("xs=")) {
                                android.util.Log.d("Gatekeeper", "✅ FB-AUTH: Login successful, auth cookie detected!")
                                onLoginSuccess()
                            }
                        }
                    }
                loadUrl("https://m.facebook.com/login")
            }
        },
    )
}

@Suppress("FunctionName")
@Composable
private fun ColumnScope.SurgicalWebView(
    url: String,
    onLogout: () -> Unit,
) {
    BaseSurgicalWebView(
        url = url,
        modifier = Modifier.weight(1f),
        cssInjector = { currentUrl ->
            val hideList = mutableListOf("div[data-m-bubble-key=\"back_button\"]")
            val isSearchPage = currentUrl.contains("/search/")
            if (!isSearchPage) {
                hideList.add("#m_newsfeed_stream")
                hideList.add("#stories_tray")
                hideList.add("#m_story_permalink_view")
            }
            val isRootList = currentUrl.matches(Regex(".*/(groups|events)/?(\\?.*)?$"))
            if (isRootList) {
                hideList.add("div[role=\"button\"][aria-label=\"Back\"]")
                hideList.add("div[role=\"button\"][aria-label=\"back\"]")
                hideList.add("a[data-sigil=\"MBackNavBarClick\"]")
            }
            hideList.joinToString(", ") + " { display: none !important; }"
        },
        networkBlocklist = emptyList(),
        onLogout = onLogout,
        jailRoot = url,
    )
}
