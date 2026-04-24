package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import java.net.URLEncoder

class YouTubeSurgicalBridge {
    @android.webkit.JavascriptInterface
    fun saveVideo(videoId: String, title: String, channel: String) {
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = videoId,
                title = title,
                channelName = channel,
                source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                currentTimestamp = System.currentTimeMillis()
            )
        )
    }

    @android.webkit.JavascriptInterface
    fun dumpHtml(html: String) {
        android.util.Log.d("GatekeeperHTML", "--- START HTML DUMP ---")
        val chunkSize = 3000
        var i = 0
        while (i < html.length) {
            val end = kotlin.math.min(html.length, i + chunkSize)
            android.util.Log.d("GatekeeperHTML", html.substring(i, end))
            i += chunkSize
        }
        android.util.Log.d("GatekeeperHTML", "--- END HTML DUMP ---")
    }
}

@Suppress("FunctionName")
@Composable
fun CleanYouTubeDialog(onDismiss: () -> Unit) {
    val state by GatekeeperStateManager.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Surgical Search", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Find exactly what you need. No rabbit holes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.OpenPinnedWebsite(
                                    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com"
                                )
                            )
                            onDismiss()
                        },
                        text = "Auth",
                        isWarning = true,
                    )
                    IndustrialButton(
                        onClick = onDismiss,
                        text = "Exit",
                        isWarning = true,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IndustrialTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search YouTube...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                val encoded = URLEncoder.encode(query, "UTF-8")
                                currentUrl = "https://m.youtube.com/results?search_query=$encoded"
                            }
                        }
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IndustrialButton(
                    onClick = {
                        if (query.isNotBlank()) {
                            val encoded = URLEncoder.encode(query, "UTF-8")
                            currentUrl = "https://m.youtube.com/results?search_query=$encoded"
                        }
                    },
                    text = "Search",
                    enabled = query.isNotBlank()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentUrl.isNotBlank()) {
                val bridge = remember { YouTubeSurgicalBridge() }
                BaseSurgicalWebView(
                    url = currentUrl,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    cssInjector = {
                        """
                        .theme-header, 
                        ytm-mobile-topbar-renderer, 
                        ytm-pivot-bar-renderer, 
                        ytm-reel-shelf-renderer, 
                        ytm-item-section-renderer[section-identifier="related-searches"],
                        ytm-chip-cloud-renderer,
                        .pivot-bar-container,
                        [role="tablist"],
                        a[href="/"],
                        a[href^="/shorts"],
                        a[href="/feed/subscriptions"],
                        a[href="/feed/library"],
                        a[href="/feed/you"] { display: none !important; }
                        """.trimIndent()
                    },
                    jsInterfaceObj = bridge,
                    jsInterfaceName = "GatekeeperBridge",
                    jsInjector = {
                        """
                        (function() {
                            if (window.gatekeeperObserver) return;
                            
                            // Log HTML for debugging
                            setTimeout(function() {
                                if (window.GatekeeperBridge) {
                                    window.GatekeeperBridge.dumpHtml(document.documentElement.outerHTML);
                                }
                            }, 4000);

                            function injectButtons() {
                                var videos = document.querySelectorAll('ytm-video-with-context-renderer');
                                videos.forEach(function(video) {
                                    if (video.querySelector('.gatekeeper-add-btn')) return;
                                    
                                    var a = video.querySelector('a');
                                    if (!a || !a.href) return;
                                    
                                    var href = a.href;
                                    var videoIdMatch = href.match(/[?&]v=([^&]+)/);
                                    if (!videoIdMatch) return;
                                    var videoId = videoIdMatch[1];
                                    
                                    var titleEl = video.querySelector('.media-item-headline');
                                    var title = titleEl ? titleEl.innerText : 'Unknown Video';
                                    
                                    var channelEl = video.querySelector('.bidi-matching-text');
                                    var channel = channelEl ? channelEl.innerText : '';
                                    
                                    var btn = document.createElement('button');
                                    btn.className = 'gatekeeper-add-btn';
                                    btn.innerText = '+ Add to Bank';
                                    btn.style.width = '100%';
                                    btn.style.padding = '12px';
                                    btn.style.marginTop = '8px';
                                    btn.style.backgroundColor = '#4AF626';
                                    btn.style.color = '#121212';
                                    btn.style.border = 'none';
                                    btn.style.borderRadius = '4px';
                                    btn.style.fontWeight = 'bold';
                                    btn.style.fontFamily = 'monospace';
                                    btn.style.fontSize = '14px';
                                    
                                    btn.onclick = function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        if (window.GatekeeperBridge) {
                                            window.GatekeeperBridge.saveVideo(videoId, title, channel);
                                            btn.innerText = 'Added ✓';
                                            btn.style.backgroundColor = '#888888';
                                            btn.disabled = true;
                                        }
                                    };
                                    
                                    video.appendChild(btn);
                                });
                            }
                            
                            window.gatekeeperObserver = new MutationObserver(function(mutations) {
                                injectButtons();
                            });
                            
                            window.gatekeeperObserver.observe(document.body, { childList: true, subtree: true });
                            injectButtons();
                        })();
                        """.trimIndent()
                    },
                    networkBlocklist = emptyList()
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Search for a specific video above.\nThe feed is disabled to protect your intent.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
    }
}
