package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    var isLoggedIn by remember {
        val cookiesD = cookieManager.getCookie("https://facebook.com") ?: ""
        val cookiesM = cookieManager.getCookie("https://m.facebook.com") ?: ""
        val allC = cookiesD + cookiesM
        mutableStateOf(allC.contains("c_user=") && allC.contains("xs="))
    }
    var forceReload by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),
    ) {
        // Header Navigation
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SurgicalNavigationRequested("https://m.facebook.com/groups/?_rdr"),
                        )
                    },
                    text = "Groups",
                    enabled = !url.contains("/groups/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SurgicalNavigationRequested("https://m.facebook.com/events/?_rdr"),
                        )
                    },
                    text = "Events",
                    enabled = !url.contains("/events/"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SurgicalNavigationRequested("https://m.facebook.com/search/?_rdr"),
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
                        isLoggedIn = false
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/login.php"),
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
            val userAgent = if (isLoggedIn) {
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
            } else {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
            }

            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                userAgent = userAgent,
                onLoginSuccess = {
                    isLoggedIn = true
                    forceReload++
                },
                onUrlChangeRequested = { requestedUrl ->
                    GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(requestedUrl))
                },
                filterRules = if (isLoggedIn) {
                    listOf(
                        SurgicalFilterRule(
                            urlCondition = { !it.contains("/search/") },
                            hiddenSelectors = listOf(
                                "div[data-m-bubble-key=\"back_button\"]",
                                "#m_newsfeed_stream",
                                "#stories_tray",
                                "#m_story_permalink_view",
                            ),
                        ),
                        SurgicalFilterRule(
                            urlCondition = { it.matches(Regex(".*/(groups|events)/?(\\?.*)?$")) },
                            hiddenSelectors = listOf(
                                "div[role=\"button\"][aria-label=\"Back\"]",
                                "div[role=\"button\"][aria-label=\"back\"]",
                                "a[data-sigil=\"MBackNavBarClick\"]",
                            ),
                        ),
                        SurgicalFilterRule(
                            urlCondition = { it.contains("/search/") },
                            hiddenSelectors = listOf("div[data-m-bubble-key=\"back_button\"]"),
                        ),
                    )
                } else {
                    emptyList()
                },
                onLogout = {
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                    isLoggedIn = false
                    GatekeeperStateManager.dispatch(GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/login.php"))
                    forceReload++
                }
            )
        }
    }
}
