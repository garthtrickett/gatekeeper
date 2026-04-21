package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun BaseSurgicalWebView(
    url: String,
    cssInjector: (String) -> String,
    networkBlocklist: List<String>,
    modifier: Modifier = Modifier,
    onPageLoaded: (String) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    jailRoot: String? = null,
) {
    var lastLoadedUrl by remember { mutableStateOf(url) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams =
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(android.graphics.Color.BLACK)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = settings.userAgentString.replace("; wv", "")

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("Gatekeeper", "🌐 BASE-WEB-CONSOLE: ${consoleMessage?.message()}")
                            return true
                        }
                    }

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            currentUrl: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, currentUrl, favicon)
                            android.util.Log.d("Gatekeeper", "📡 BASE-WEB-LOADING: $currentUrl")
                            if (currentUrl != null && currentUrl.contains("logout.php") && onLogout != null) {
                                android.util.Log.d("Gatekeeper", "🚪 BASE-WEB-AUTH: Logout detected.")
                                onLogout()
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.e("Gatekeeper", "❌ BASE-WEB-ERROR: ${error?.description} at ${request?.url}")
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            currentUrl: String?,
                        ) {
                            super.onPageFinished(view, currentUrl)
                            currentUrl?.let { onPageLoaded(it) }

                            val cssToInject = cssInjector(currentUrl ?: "")
                            if (cssToInject.isNotBlank()) {
                                val cleanCss = cssToInject.replace("\n", " ").replace("\"", "\\\"").replace("'", "\\'")
                                val js =
                                    """
                                    (function() {
                                        var styleId = 'gatekeeper-base-surgical-mask';
                                        var style = document.getElementById(styleId);
                                        if (!style) {
                                            style = document.createElement('style');
                                            style.id = styleId;
                                            document.head.appendChild(style);
                                        }
                                        style.innerHTML = "$cleanCss";
                                    })();
                                    """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            val requestUrl = request?.url?.toString() ?: ""
                            val shouldBlock = networkBlocklist.any { requestUrl.contains(it, ignoreCase = true) }
                            if (shouldBlock) {
                                android.util.Log.d("Gatekeeper", "🛡️ BASE-WEB: Blocked network request to $requestUrl")
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val newUrl = request?.url?.toString() ?: ""

                            val isAuthFlow =
                                newUrl.contains("accounts.google.com") ||
                                    newUrl.contains("myaccount.google.com") ||
                                    newUrl.contains("accounts.youtube.com")
                            if (isAuthFlow) {
                                return false
                            }

                            if (onLogout != null && (newUrl.contains("login") || newUrl.contains("checkpoint"))) {
                                onLogout()
                                return true
                            }

                            if (jailRoot != null) {
                                val isExplicitHomeFeed =
                                    newUrl == "https://m.facebook.com/" ||
                                        newUrl.startsWith("https://m.facebook.com/?") ||
                                        newUrl.contains("facebook.com/home") ||
                                        newUrl.contains("ref=logo")

                                if (isExplicitHomeFeed) {
                                    android.util.Log.d("Gatekeeper", "🛡️ Jail: Blocking redirect to Feed. Forcing current root: $jailRoot")
                                    view?.post { view.loadUrl(jailRoot) }
                                    return true
                                }
                            }

                            return false
                        }
                    }
                loadUrl(lastLoadedUrl)
            }
        },
        update = { webView ->
            if (url != lastLoadedUrl) {
                lastLoadedUrl = url
                webView.loadUrl(url)
            }
        },
    )
}
