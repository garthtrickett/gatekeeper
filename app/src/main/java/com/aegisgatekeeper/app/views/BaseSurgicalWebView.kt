package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

data class SurgicalFilterRule(
    val urlCondition: (String) -> Boolean,
    val hiddenSelectors: List<String>,
)

@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun BaseSurgicalWebView(
    url: String,
    filterRules: List<SurgicalFilterRule> = emptyList(),
    networkBlocklist: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onPageLoaded: (String) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    jailRoot: String? = null,
    jsInterfaceObj: Any? = null,
    jsInterfaceName: String? = null,
    jsInjector: ((String) -> String)? = null,
    userAgent: String? = null,
    onLoginSuccess: () -> Unit = {},
    onInterceptUrlChange: ((WebView, String) -> Boolean)? = null,
) {
    var lastLoadedUrl by remember { mutableStateOf(url) }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                var jailRedirectCount = 0
                var lastJailRedirectTime = 0L

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

                if (userAgent != null) {
                    settings.userAgentString = userAgent
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                if (jsInterfaceObj != null && jsInterfaceName != null) {
                    addJavascriptInterface(jsInterfaceObj, jsInterfaceName)
                }

                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("Gatekeeper", "🌐 BASE-WEB-CONSOLE: ${consoleMessage?.message()}")
                            return true
                        }
                    }

                webViewClient =
                    object : WebViewClient() {
                        @Volatile
                        private var currentDocUrl: String = url

                        override fun onPageStarted(
                            view: WebView?,
                            currentUrl: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, currentUrl, favicon)
                            currentDocUrl = currentUrl ?: ""
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
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e(
                                    "Gatekeeper",
                                    "🚨 BASE-WEB-ERROR: ${error?.errorCode} - ${error?.description} on URL: ${request.url}",
                                )
                            } else {
                                android.util.Log.e("Gatekeeper", "❌ BASE-WEB-ERROR: ${error?.description} at ${request?.url}")
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?,
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e(
                                    "Gatekeeper",
                                    "🚨 BASE-WEB-HTTP-ERROR: ${errorResponse?.statusCode} - ${errorResponse?.reasonPhrase} on URL: ${request.url}",
                                )
                            }
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            currentUrl: String?,
                        ) {
                            super.onPageFinished(view, currentUrl)
                            android.util.Log.d("Gatekeeper", "🏁 BASE-WEB-FINISHED: $currentUrl")
                            currentUrl?.let { onPageLoaded(it) }

                            val currentCookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                            val isNowLoggedIn = currentCookies.contains("c_user=") && currentCookies.contains("xs=")

                            if (jailRoot == null && isNowLoggedIn) {
                                android.util.Log.d("Gatekeeper", "✅ AUTH-SUCCESS: Login detected. Triggering UA switch.")
                                onLoginSuccess()
                                return
                            }

                            val currentJailRoot = view?.tag as? String
                            if (currentJailRoot != null) {
                                val isExplicitHomeFeed =
                                    currentUrl == "https://m.facebook.com/" ||
                                        currentUrl?.startsWith("https://m.facebook.com/?") == true ||
                                        currentUrl?.contains("facebook.com/home") == true ||
                                        currentUrl?.contains("ref=logo") == true

                                if (isExplicitHomeFeed) {
                                    val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                                    if (onLogout != null && !cookies.contains("c_user=")) {
                                        android.util.Log.d(
                                            "Gatekeeper",
                                            "🚪 Jail: User is logged out. Triggering logout instead of jailing.",
                                        )
                                        onLogout()
                                        return
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastJailRedirectTime < 5000L) {
                                        jailRedirectCount++
                                    } else {
                                        jailRedirectCount = 1
                                    }
                                    lastJailRedirectTime = now

                                    if (jailRedirectCount > 3) {
                                        android.util.Log.e("Gatekeeper", "🛑 Jail: Redirect loop detected. Triggering logout.")
                                        onLogout?.invoke()
                                        return
                                    }

                                    android.util.Log.d(
                                        "Gatekeeper",
                                        "🛡️ Jail: Reached feed after redirect. Redirecting to jail root: $currentJailRoot",
                                    )
                                    view?.loadUrl(currentJailRoot)
                                    return
                                }
                            }

                            val filterEngine = com.aegisgatekeeper.app.di.GlobalDI.component.surgicalFilterEngine
                            val engineCss = filterEngine.getCosmeticCss(currentUrl ?: "")

                            val activeSelectors =
                                filterRules
                                    .filter { it.urlCondition(currentUrl ?: "") }
                                    .flatMap { it.hiddenSelectors }
                                    .distinct()

                            val combinedCss =
                                buildString {
                                    append(engineCss)
                                    if (activeSelectors.isNotEmpty()) {
                                        if (isNotEmpty()) append(" ")
                                        append(activeSelectors.joinToString(", "))
                                        append(" { display: none !important; }")
                                    }
                                }

                            if (combinedCss.isNotBlank()) {
                                val cleanCss = combinedCss
                                val js =
                                    """
                                    (function() {
                                        function injectCSS() {
                                            var styleId = 'gatekeeper-base-surgical-mask';
                                            var style = document.getElementById(styleId);
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = styleId;
                                                style.textContent = "$cleanCss";
                                                document.documentElement.appendChild(style);
                                            } else if (style.textContent !== "$cleanCss") {
                                                style.textContent = "$cleanCss";
                                            }
                                        }
                                        injectCSS();
                                        if (!window.gkCssObserver) {
                                            window.gkCssObserver = new MutationObserver(injectCSS);
                                            window.gkCssObserver.observe(document.documentElement, { childList: true, subtree: true });
                                        }
                                    })();
                                    """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }

                            jsInjector?.invoke(currentUrl ?: "")?.let { jsToInject ->
                                if (jsToInject.isNotBlank()) {
                                    view?.evaluateJavascript(jsToInject, null)
                                }
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            val requestUrl = request?.url?.toString() ?: ""
                            val documentUrl = if (request?.isForMainFrame == true) requestUrl else currentDocUrl

                            val filterEngine = com.aegisgatekeeper.app.di.GlobalDI.component.surgicalFilterEngine
                            if (filterEngine.shouldBlockRequest(requestUrl, documentUrl)) {
                                android.util.Log.d("Gatekeeper", "🛡️ BASE-WEB: Blocked network request to $requestUrl")
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }

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
                            android.util.Log.d("Gatekeeper", "🔀 BASE-WEB-REDIRECT: $newUrl")

                            if (newUrl.contains("m.facebook.com/login")) {
                                val desktopLoginUrl = newUrl.replace("m.facebook.com", "www.facebook.com")
                                android.util.Log.d("Gatekeeper", "🛡️ AUTH-DETOUR: Forcing desktop login via: $desktopLoginUrl")
                                view?.loadUrl(desktopLoginUrl)
                                return true
                            }

                            if (newUrl.startsWith("intent://") || newUrl.startsWith("fb://") || newUrl.startsWith("android-app://")) {
                                android.util.Log.d("Gatekeeper", "🛡️ BASE-WEB: Intercepting deep link: $newUrl")

                                if (newUrl.startsWith("intent://")) {
                                    try {
                                        val intent = android.content.Intent.parseUri(newUrl, android.content.Intent.URI_INTENT_SCHEME)
                                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                        if (fallbackUrl != null) {
                                            android.util.Log.d("Gatekeeper", "🌐 BASE-WEB: Navigating to deep link fallback: $fallbackUrl")
                                            view?.loadUrl(fallbackUrl)
                                            return true
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Gatekeeper", "❌ BASE-WEB: Failed to parse intent fallback: ${e.message}")
                                    }
                                }
                                return true
                            }

                            val isAuthFlow =
                                newUrl.contains("accounts.google.com") ||
                                    newUrl.contains("myaccount.google.com") ||
                                    newUrl.contains("accounts.youtube.com")
                            if (isAuthFlow) {
                                return false
                            }

                            val currentJailRoot = view?.tag as? String
                            if (currentJailRoot != null) {
                                if (newUrl.contains("www.facebook.com")) {
                                    val mobileUrl =
                                        newUrl.replace("www.facebook.com", "m.facebook.com")
                                    android.util.Log.d("Gatekeeper", "🛡️ Mobile-Forcing: Rewriting to $mobileUrl")
                                    view?.loadUrl(mobileUrl)
                                    return true
                                }

                                val isExplicitHomeFeed =
                                    newUrl == "https://m.facebook.com/" ||
                                        newUrl.startsWith("https://m.facebook.com/?") ||
                                        newUrl.contains("facebook.com/home") ||
                                        newUrl.contains("ref=logo")

                                if (isExplicitHomeFeed) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                                        request?.isRedirect == true
                                    ) {
                                        android.util.Log.d("Gatekeeper", "🛡️ Jail: Allowing redirect to Feed to preserve cookies.")
                                        return false
                                    }
                                    android.util.Log.d(
                                        "Gatekeeper",
                                        "🛡️ Jail: Blocking navigation to Feed. Forcing current root: $currentJailRoot",
                                    )
                                    view?.post { view.loadUrl(currentJailRoot) }
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
            webView.tag = jailRoot

            if (userAgent != null && webView.settings.userAgentString != userAgent) {
                webView.settings.userAgentString = userAgent
            }
            if (url != lastLoadedUrl) {
                val handled = onInterceptUrlChange?.invoke(webView, url) ?: false
                if (!handled) {
                    webView.loadUrl(url)
                }
                lastLoadedUrl = url
            }
        },
    )
}
