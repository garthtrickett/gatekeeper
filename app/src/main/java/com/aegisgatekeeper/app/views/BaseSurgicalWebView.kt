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
    onUrlChangeRequested: (String) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    jsInterfaceObj: Any? = null,
    jsInterfaceName: String? = null,
    jsInjector: ((String) -> String)? = null,
    userAgent: String? = null,
    onLoginSuccess: () -> Unit = {},
) {
    var lastLoadedUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
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

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    // Thread-safe URL tracking for background callbacks
                    @Volatile
                    private var currentDocUrl: String = url

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        currentDocUrl = url ?: ""
                    }

                    override fun onPageFinished(view: WebView?, currentUrl: String?) {
                        super.onPageFinished(view, currentUrl)
                        val actualUrl = currentUrl ?: ""
                        currentDocUrl = actualUrl
                        
                        lastLoadedUrl = actualUrl
                        onPageLoaded(actualUrl)

                        val filterEngine = com.aegisgatekeeper.app.di.GlobalDI.component.surgicalFilterEngine
                        val combinedCss = buildString {
                            append(filterEngine.getCosmeticCss(actualUrl))
                            filterRules.filter { it.urlCondition(actualUrl) }
                                .flatMap { it.hiddenSelectors }
                                .distinct()
                                .forEach { append("$it { display: none !important; } ") }
                        }

                        if (combinedCss.isNotBlank()) {
                            val js = "var style = document.getElementById('gk-surgical-mask'); " +
                                     "if(!style) { style = document.createElement('style'); style.id = 'gk-surgical-mask'; document.head.appendChild(style); } " +
                                     "style.textContent = \"$combinedCss\";"
                            view?.evaluateJavascript(js, null)
                        }

                        jsInjector?.invoke(actualUrl)?.let { view?.evaluateJavascript(it, null) }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val newUrl = request?.url?.toString() ?: return true
                        
                        if (newUrl.contains("accounts.google.com") || newUrl.contains("accounts.youtube.com")) {
                            return false
                        }

                        if (newUrl.startsWith("intent://") || newUrl.startsWith("fb://") || newUrl.startsWith("android-app://")) {
                            return true 
                        }

                        if (request?.isForMainFrame == true) {
                            if (newUrl != lastLoadedUrl) {
                                android.util.Log.d("Gatekeeper", "📥 WebView Navigation Intent: $newUrl")
                                onUrlChangeRequested(newUrl)
                            }
                            return true 
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        // FIX: Use currentDocUrl instead of calling view?.url (which crashes on background thread)
                        if (com.aegisgatekeeper.app.di.GlobalDI.component.surgicalFilterEngine.shouldBlockRequest(reqUrl, currentDocUrl)) {
                            return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
            }
        },
        update = { webView ->
            if (url.isNotBlank() && url != lastLoadedUrl) {
                android.util.Log.d("Gatekeeper", "📡 State forcing WebView Load: $url")
                webView.loadUrl(url)
                lastLoadedUrl = url
            }
        }
    )
}
