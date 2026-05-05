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
@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun SurgicalFacebookScreen(
    url: String,
    onClose: () -> Unit,
) {
    val cookieManager = remember { CookieManager.getInstance() }
    var isLoggedIn by remember {
        val cookiesD = cookieManager.getCookie("https://facebook.com") ?: ""
        val cookiesM = cookieManager.getCookie("https://m.facebook.com") ?: ""
        val allC = cookiesD + cookiesM
        mutableStateOf(allC.contains("c_user=") && allC.contains("xs="))
    }
    var forceReload by remember { mutableStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
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
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/?_rdr"),
                        )
                    },
                    text = "Groups",
                    enabled = !url.contains("/groups/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/events/?_rdr"),
                        )
                    },
                    text = "Events",
                    enabled = !url.contains("/events/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/search/?_rdr"),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/search/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        // Force a state update with a cache-busting param to trigger WebView reload
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook(
                                "https://m.facebook.com/groups/?_rdr&reload=${System.currentTimeMillis()}",
                            ),
                        )
                        forceReload++
                    },
                    text = "Logout",
                    isWarning = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
        }

        androidx.compose.runtime.key(forceReload, isLoggedIn) {
            val userAgent =
                if (isLoggedIn) {
                    // Once logged in, use a clean mobile UA to get the correct mobile layout.
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                } else {
                    // Before login, use a Desktop UA to bypass the 2FA/login redirect loop.
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
                }

            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                userAgent = userAgent,
                onLoginSuccess = {
                    android.util.Log.d("Gatekeeper", "🔄 FB-AUTH: Login Success. Switching to Mobile UA.")
                    isLoggedIn = true
                    forceReload++
                },
                onInterceptUrlChange = { webView, newUrl ->
                    if (isLoggedIn) {
                        val targetPath =
                            when {
                                newUrl.contains("/groups/") -> "/groups/"
                                newUrl.contains("/events/") -> "/events/"
                                newUrl.contains("/search/") -> "/search/"
                                else -> null
                            }
                        if (targetPath != null) {
                            val js =
                                """
                                (function(targetPath) {
                                    try {
                                        // Prioritize the bottom navigation tab links, then fall back to any link
                                        var links = Array.from(document.querySelectorAll('a[role="tab"]')).concat(Array.from(document.querySelectorAll('a')));
                                        var targetLink = links.find(a => a.href && a.href.includes(targetPath));
                                        
                                        if (targetLink) {
                                            // Click the inner element to correctly trigger React's synthetic event system without a TypeError
                                            var clickTarget = targetLink.firstElementChild || targetLink;
                                            clickTarget.click();
                                            return 'clicked';
                                        }
                                        return 'not_found';
                                    } catch(e) {
                                        return 'error_' + e.message;
                                    }
                                })('$targetPath');
                                """.trimIndent()

                            webView.evaluateJavascript(js) { result ->
                                if (result == "\"clicked\"") {
                                    android.util.Log.d("Gatekeeper", "✨ SPA hack succeeded. Soft navigating to: $newUrl")
                                } else {
                                    android.util.Log.d("Gatekeeper", "🛡️ SPA hack failed ($result). Falling back to hard load: $newUrl")
                                    webView.loadUrl(newUrl)
                                }
                            }
                            true // Handled asynchronously
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
                cssInjector = { currentUrl ->
                    if (isLoggedIn) {
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
                    } else {
                        ""
                    }
                },
                networkBlocklist = emptyList(),
                onLogout = {
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                    isLoggedIn = false
                    GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/login.php"))
                    forceReload++
                },
                onPageLoaded = { },
                jailRoot = if (isLoggedIn) url else null,
            )
        }
    }
}
    url: String,
    onClose: () -> Unit,
) {
    val cookieManager = remember { CookieManager.getInstance() }
    var isLoggedIn by remember {
        val cookiesD = cookieManager.getCookie("https://facebook.com") ?: ""
        val cookiesM = cookieManager.getCookie("https://m.facebook.com") ?: ""
        val allC = cookiesD + cookiesM
        mutableStateOf(allC.contains("c_user=") && allC.contains("xs="))
    }
    var forceReload by remember { mutableStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
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
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/?_rdr"),
                        )
                    },
                    text = "Groups",
                    enabled = !url.contains("/groups/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/events/?_rdr"),
                        )
                    },
                    text = "Events",
                    enabled = !url.contains("/events/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/search/?_rdr"),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/search/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        // Force a state update with a cache-busting param to trigger WebView reload
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook(
                                "https://m.facebook.com/groups/?_rdr&reload=${System.currentTimeMillis()}",
                            ),
                        )
                        forceReload++
                    },
                    text = "Logout",
                    isWarning = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
        }

        androidx.compose.runtime.key(forceReload, isLoggedIn) {
            val userAgent =
                if (isLoggedIn) {
                    // Once logged in, use a clean mobile UA to get the correct mobile layout.
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                } else {
                    // Before login, use a Desktop UA to bypass the 2FA/login redirect loop.
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
                }

            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                userAgent = userAgent,
                onLoginSuccess = {
                    android.util.Log.d("Gatekeeper", "🔄 FB-AUTH: Login Success. Switching to Mobile UA.")
                    isLoggedIn = true
                    forceReload++
                },
                onInterceptUrlChange = { webView, newUrl ->
                    if (isLoggedIn) {
                        val targetPath =
                            when {
                                newUrl.contains("/groups/") -> "/groups/"
                                newUrl.contains("/events/") -> "/events/"
                                newUrl.contains("/search/") -> "/search/"
                                else -> null
                            }
                        if (targetPath != null) {
                            val js =
                                """
                                (function(targetPath) {
                                    try {
                                        // Prioritize the bottom navigation tab links, then fall back to any link
                                        var links = Array.from(document.querySelectorAll('a[role="tab"]')).concat(Array.from(document.querySelectorAll('a')));
                                        var targetLink = links.find(a => a.href && a.href.includes(targetPath));
                                        
                                        if (targetLink) {
                                            // Click the inner element to correctly trigger React's synthetic event system without a TypeError
                                            var clickTarget = targetLink.firstElementChild || targetLink;
                                            clickTarget.click();
                                            return 'clicked';
                                        }
                                        return 'not_found';
                                    } catch(e) {
                                        return 'error_' + e.message;
                                    }
                                })('$targetPath');
                                """.trimIndent()

                            webView.evaluateJavascript(js) { result ->
                                if (result == "\"clicked\"") {
                                    android.util.Log.d("Gatekeeper", "✨ SPA hack succeeded. Soft navigating to: $newUrl")
                                } else {
                                    android.util.Log.d("Gatekeeper", "🛡️ SPA hack failed ($result). Falling back to hard load: $newUrl")
                                    webView.loadUrl(newUrl)
                                }
                            }
                            true // Handled asynchronously
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
                cssInjector = { currentUrl ->
                    if (isLoggedIn) {
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
                    } else {
                        ""
                    }
                },
                networkBlocklist = emptyList(),
                onLogout = {
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                    isLoggedIn = false
                    GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/login.php"))
                    forceReload++
                },
                onPageLoaded = { },
                jailRoot = if (isLoggedIn) url else null,
            )
        }
    }
}
