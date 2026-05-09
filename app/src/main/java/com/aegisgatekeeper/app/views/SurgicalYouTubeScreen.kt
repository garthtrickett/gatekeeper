package com.aegisgatekeeper.app.views

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun SurgicalYouTubeScreen(
    url: String,
    onClose: () -> Unit,
) {
    val state by GatekeeperStateManager.state.collectAsState()
    val safeChannelIds =
        remember(state.data.safeYouTubeChannels) {
            state.data.safeYouTubeChannels.keys
                .joinToString(",") { "'$it'" }
        }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),
    ) {
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
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/results?search_query="),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/results?search_query"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SurgicalNavigationRequested("gatekeeper://safe_channels"),
                        )
                    },
                    text = "Safe Channels",
                    enabled = url != "gatekeeper://safe_channels",
                    invertEnabledColor = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
        }

        if (url == "gatekeeper://safe_channels") {
            safeChannelsList(
                onNavigate = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(it))
                },
            )
        } else {
            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                onUrlChangeRequested = { requestedUrl ->
                    GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(requestedUrl))
                },
                jsInterfaceObj = YouTubeSurgicalBridge(),
                jsInterfaceName = "AndroidBridge",
                jsInjector = { currentUrl ->
                    android.util.Log.d("Gatekeeper", "💉 Injecting JS for URL: $currentUrl")
                    val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                    """
                    (function() {
                        console.log('GK_DEBUG: Surgical Script Initializing (Link Neutralizer Mode)');
                        $initSafeChannels

                        if (window.gkObserver) window.gkObserver.disconnect();

                                            function applyFilters() {
                            var href = window.location.href;
                            if (!href || href === 'about:blank') return;
                            if (typeof AndroidBridge !== 'undefined' && Math.random() < 0.1) {
                                 // Periodically log heartbeat to ensure observer is alive
                                 AndroidBridge.logMessage('GK_HEARTBEAT: ' + href);
                            }

                            // 1. NEUTRALIZE LINKS
                            document.querySelectorAll('a[href*="/watch?v="], a[href*="/shorts/"], a#logo, a[href="/"], a[href^="/?"]').forEach(function(link) {
                                if (link.dataset.gkNeutralized) return;
                                
                                link.dataset.gkOriginalHref = link.href;
                                link.href = 'javascript:void(0);';
                                link.dataset.gkNeutralized = 'true';
                                
                                link.onclick = function(e) {
                                    e.stopPropagation();
                                    if (typeof AndroidBridge !== 'undefined') {
                                        AndroidBridge.logMessage('🛡️ Neutralized Link Tapped: ' + link.dataset.gkOriginalHref);
                                    }
                                    return false;
                                };
                            });

                                                                            // 2. COSMETIC NUKE
                            const selectors =[
                                'ytm-reel-shelf-renderer', 
                                'ytm-pivot-bar-item-renderer[tab-id="FEshorts"]', 
                                'a[href^="/shorts/"]', 
                                '.reel-shelf-header-view-model-wiz',
                                'ytm-pivot-bar-renderer', 
                                'a#logo', 
                                '.ytm-logo-container',
                                '.ytm-header-logo',
                                'yt-icon.ytm-home-logo',
                                '.mobile-topbar-logo'
                            ];
                                                        function nuke(root) {
                                selectors.forEach(s => root.querySelectorAll(s).forEach(el => el.style.display = 'none'));
                                var shelfTags =['ytm-item-section-renderer', 'ytm-shelf-renderer', 'ytm-rich-shelf-renderer', 'ytm-channel-video-shelf-renderer'];
                                root.querySelectorAll(shelfTags.join(', ')).forEach(function(shelf) {
                                    var header = shelf.querySelector('h2, h3, .shelf-title');
                                    if (header && /latest from/i.test(header.textContent)) {
                                        shelf.style.display = 'none';
                                    }
                                });
                                root.querySelectorAll('*').forEach(el => {
                                    if (el.shadowRoot) nuke(el.shadowRoot);
                                });
                            }
                            nuke(document);

                                                                                                                    // 3. CHANNEL INTERFACE (Removed)

                                            // 4. VIDEO RESULTS INTERFACE
                            if (href.includes('/results') || href.includes('/channel/') || href.includes('/@') || href.includes('/c/')) {
                                var videoElements = document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-media-item');
                                if (videoElements.length > 0 && typeof AndroidBridge !== 'undefined' && Math.random() < 0.05) {
                                    AndroidBridge.logMessage('GK_DEBUG: Video UI Scan. Found ' + videoElements.length + ' potential items at ' + href);
                                }
                                
                                videoElements.forEach(function(video) {
                                    if (video.dataset.gkHandled) return;
                                    
                                    // Use a broader search for the link since ytm-media-item structure varies
                                    var link = video.querySelector('a[data-gk-neutralized="true"]') || video.querySelector('a[href*="/watch?v="]');
                                    
                                    if (!link && typeof AndroidBridge !== 'undefined' && Math.random() < 0.01) {
                                        AndroidBridge.logMessage('GK_DEBUG: Video element found but no watch link detected inside: ' + video.tagName);
                                    }
                                    if (!link || !link.dataset.gkOriginalHref) return;
                                    
                                    var vIdMatch = link.dataset.gkOriginalHref.match(/v=([^&]+)/);
                                    if (!vIdMatch) return;
                                    var vId = vIdMatch[1];
                                    
                                    video.dataset.gkHandled = 'true';
                                    
                                                                    var titleEl = video.querySelector('h3.media-item-headline, h4.media-item-headline, .media-item-headline, h3, h4, .yt-core-attributed-string');
                                    var title = titleEl ? titleEl.textContent.trim() : 'Video';
                                    var channelEl = video.querySelector('ytm-badge-and-byline-renderer .yt-formatted-string, .ytm-badge-and-byline-renderer-string');
                                    var pageTitle = document.title ? document.title.replace(' - YouTube', '').trim() : '';
                                    var channelName = channelEl ? channelEl.textContent.trim() : (pageTitle || 'Channel');
                                    
                                    var durationEl = video.querySelector('ytm-thumbnail-overlay-time-status-renderer span, .ytm-thumbnail-overlay-time-status-renderer, .badge-shape-wiz__text');
                                    var duration = durationEl ? durationEl.textContent.trim() : '';
                                    
                                    if (!duration || !duration.includes(':')) {
                                        var timestampMatch = video.innerText.match(/\d+:\d+(?::\d+)?/);
                                        if (timestampMatch) duration = timestampMatch[0];
                                    }

                                                                    var btn = document.createElement('button');
                                    btn.innerText = '+ BANK';
                                    btn.style.cssText = 'background-color:#4AF626; color:#000; border:none; padding:12px 16px; font-weight:bold; border-radius:4px; width:100%; margin-top:8px;';
                                    btn.onclick = function(e) {
                                        e.preventDefault(); e.stopPropagation();
                                        AndroidBridge.saveVideo(vId, title, channelName, duration);
                                        btn.innerText = 'SAVED ✓'; btn.style.backgroundColor = '#888';
                                    };
                                    video.appendChild(btn);

                                    var channelAnchor = video.querySelector('ytm-badge-and-byline-renderer a[href*="/channel/"], ytm-badge-and-byline-renderer a[href*="/@"], a[href*="/channel/"], a[href*="/@"]');
                                    var channelId = '';
                                    if (channelAnchor) {
                                        var rawPath = channelAnchor.getAttribute('href');
                                        if (rawPath.includes('/channel/')) {
                                            channelId = rawPath.split('/channel/')[1].split('/')[0];
                                        } else if (rawPath.includes('/@')) {
                                            channelId = '@' + rawPath.split('/@')[1].split('/')[0];
                                        }
                                    }
                                    if (channelId) {
                                        var isSafe = window.gkSafeChannels.includes(channelId);
                                                                            var safeBtn = document.createElement('button');
                                        safeBtn.innerText = isSafe ? 'SAFE ✅' : 'SET THIS CHANNEL AS SAFE';
                                        safeBtn.style.cssText = 'background-color:' + (isSafe ? '#4CAF50' : '#444') + '; color:#fff; border:none; padding:12px 16px; font-weight:bold; border-radius:4px; width:100%; margin-top:4px;';
                                        safeBtn.onclick = function(e) {
                                            e.preventDefault(); e.stopPropagation();
                                            AndroidBridge.toggleSafeChannel(channelId, channelName);
                                            isSafe = !isSafe;
                                            if (isSafe) {
                                                window.gkSafeChannels.push(channelId);
                                            } else {
                                                                                        window.gkSafeChannels = window.gkSafeChannels.filter(function(id) { return id !== channelId; });
                                            }
                                            safeBtn.innerText = isSafe ? 'SAFE ✅' : 'SET THIS CHANNEL AS SAFE';
                                            safeBtn.style.backgroundColor = isSafe ? '#4CAF50' : '#444';
                                        };
                                        video.appendChild(safeBtn);
                                    }
                                });
                            }

                            // 5. DISABLE AUTOPLAY
                            document.querySelectorAll('video, ytm-inline-preview-player-renderer').forEach(function(el) {
                                el.remove();
                            });
                        }

                        window.gkObserver = new MutationObserver(applyFilters);
                        window.gkObserver.observe(document.body, { childList: true, subtree: true });
                        applyFilters();
                    })();
                    """.trimIndent()
                },
            )
        }
    }
}

@Composable
fun safeChannelsList(onNavigate: (String) -> Unit) {
    val state by GatekeeperStateManager.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Safe Channels", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.data.safeYouTubeChannels.isEmpty()) {
            Text("No safe channels added yet. Search for a channel and click 'SET THIS CHANNEL AS SAFE'.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    state.data.safeYouTubeChannels.entries
                        .toList(),
                ) { (id, name) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IndustrialButton(
                                    onClick = {
                                        val baseUrl =
                                            if (id.startsWith(
                                                    "@",
                                                )
                                            ) {
                                                "https://m.youtube.com/$id"
                                            } else {
                                                "https://m.youtube.com/channel/$id"
                                            }
                                        onNavigate("$baseUrl/videos")
                                    },
                                    text = "View",
                                )
                                IndustrialButton(
                                    onClick = {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.ToggleSafeYouTubeChannel(id, name))
                                    },
                                    text = "Remove",
                                    isWarning = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
