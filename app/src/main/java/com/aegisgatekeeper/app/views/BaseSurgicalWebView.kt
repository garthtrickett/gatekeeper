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
    jsInterfaceObj: Any? = null,
    jsInterfaceName: String? = null,
    jsInjector: ((String) -> String)? = null,
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
                
                val isFacebook = url.contains("facebook.com", ignoreCase = true)
                val isGoogle = url.contains("youtube.com", ignoreCase = true) || url.contains("google.com", ignoreCase = true)
                
                if (isFacebook) {
                    // Facebook explicitly blocks Android WebViews from completing 2FA flows (throwing 404s) to prevent phishing.
                    // Android WebViews inject an undeletable `X-Requested-With` header. 
                    // Spoofing an iOS Safari User-Agent bypasses their Android-specific WAF entirely.
                    settings.userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1"
                } else if (isGoogle) {
                    // Completely mask the WebView to prevent Google from serving 404s or rejecting logins.
                    // Both "; wv" and "Version/4.0 " are unique fingerprints of Android WebViews.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
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
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e("Gatekeeper", "🚨 BASE-WEB-ERROR: ${error?.errorCode} - ${error?.description} on URL: ${request.url}")
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
                                android.util.Log.e("Gatekeeper", "🚨 BASE-WEB-HTTP-ERROR: ${errorResponse?.statusCode} - ${errorResponse?.reasonPhrase} on URL: ${request.url}")
                            }
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            currentUrl: String?,
                        ) {
                            super.onPageFinished(view, currentUrl)
                            android.util.Log.d("Gatekeeper", "🏁 BASE-WEB-FINISHED: $currentUrl")
                            val cookies = CookieManager.getInstance().getCookie(currentUrl)
                            android.util.Log.d("Gatekeeper", "🍪 COOKIE-STATE-CHECK | Has Cookies: ${!cookies.isNullOrEmpty()}")
                            currentUrl?.let { onPageLoaded(it) }

                            if (jailRoot != null) {
                                val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                                val isNowLoggedIn = cookies.contains("c_user=") && cookies.contains("xs=")
                                val isOnDesktopHomepage = currentUrl?.contains("www.facebook.com") == true && (currentUrl.endsWith("/") || currentUrl.endsWith("home.php"))

                                // AUTH-DETOUR STEP 4: Detect successful desktop login and redirect back to mobile surgical root.
                                if (isNowLoggedIn && isOnDesktopHomepage) {
                                    android.util.Log.d("Gatekeeper", "✅ AUTH-DETOUR: Desktop login complete. Redirecting to mobile surgical root: $jailRoot")
                                    view?.loadUrl(jailRoot)
                                    return
                                }

                                val isExplicitHomeFeed =
                                    currentUrl == "https://m.facebook.com/" ||
                                        currentUrl?.startsWith("https://m.facebook.com/?") == true ||
                                        currentUrl?.contains("facebook.com/home") == true ||
                                        currentUrl?.contains("ref=logo") == true

                                if (isExplicitHomeFeed) {
                                    val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                                    if (onLogout != null && !cookies.contains("c_user=")) {
                                        android.util.Log.d("Gatekeeper", "🚪 Jail: User is logged out. Triggering logout instead of jailing.")
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

                                    android.util.Log.d("Gatekeeper", "🛡️ Jail: Reached feed after redirect. Redirecting to jail root.")
                                    view?.loadUrl(jailRoot)
                                    return
                                }
                            }

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
                                        style.textContent = "$cleanCss";
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

                            // AUTH-DETOUR STEP 1 & 2: If we are being sent to the mobile login, intercept and force the desktop version.
                            if (newUrl.contains("m.facebook.com/login")) {
                                val desktopLoginUrl = newUrl.replace("m.facebook.com", "www.facebook.com")
                                android.util.Log.d("Gatekeeper", "🛡️ AUTH-DETOUR: Forcing desktop login via: $desktopLoginUrl")
                                view?.loadUrl(desktopLoginUrl)
                                return true // We've handled the navigation.
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

                            // We no longer eagerly logout on "login" or "checkpoint" URLs.
                            // Facebook handles these flows gracefully, and wiping cookies breaks them.

                            if (jailRoot != null) {
                                val isExplicitHomeFeed =
                                    newUrl == "https://m.facebook.com/" ||
                                        newUrl.startsWith("https://m.facebook.com/?") ||
                                        newUrl.contains("facebook.com/home") ||
                                        newUrl.contains("ref=logo")

                                if (isExplicitHomeFeed) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && request?.isRedirect == true) {
                                        android.util.Log.d("Gatekeeper", "🛡️ Jail: Allowing redirect to Feed to preserve cookies.")
                                        return false
                                    }
                                    android.util.Log.d("Gatekeeper", "🛡️ Jail: Blocking navigation to Feed. Forcing current root: $jailRoot")
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
