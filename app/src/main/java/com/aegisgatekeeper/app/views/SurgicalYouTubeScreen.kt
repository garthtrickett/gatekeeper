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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
                            GatekeeperAction.OpenSurgicalYouTube(
                                "https://m.youtube.com/feed/channels",
                            ),
                        )
                    },
                    text = "Subscriptions",
                    enabled = !url.contains("/feed/channels"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalYouTube(
                                "https://m.youtube.com/results?search_query=podcasts",
                            ),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/results?search_query"),
                    invertEnabledColor = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IndustrialButton(onClick = onClose, text = "Exit", isWarning = true)
        }

        androidx.compose.runtime.key(forceReload) {
            BaseSurgicalWebView(
                url = url,
                modifier = Modifier.weight(1f),
                onInterceptUrlChange = { webView, newUrl ->
                    if (newUrl.contains("youtube.com")) {
                        val targetPath =
                            when {
                                newUrl.contains("/feed/channels") -> "/feed/channels"
                                newUrl.contains("/results") -> "/results"
                                else -> null
                            }
                        if (targetPath != null) {
                            val js =
                                """
                                (function(targetPath) {
                                    try {
                                        var targetLink = document.querySelector('a[href*="' + targetPath + '"]');
                                        if (targetLink) {
                                            targetLink.click();
                                            return 'clicked';
                                        }
                                        return 'not_found';
                                    } catch(e) {
                                        return 'error_' + e.message;
                                    }
                                })('${'$'}targetPath');
                                """.trimIndent()

                            webView.evaluateJavascript(js) { result ->
                                if (result != "\"clicked\"") {
                                    webView.loadUrl(newUrl)
                                }
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
                filterRules =
                    listOf(
                        SurgicalFilterRule(
                            urlCondition = { true },
                            hiddenSelectors =
                                listOf(
                                    "ytm-pivot-bar-renderer",
                                    ".modern-sharing-ui",
                                ),
                        ),
                    ),
                networkBlocklist = emptyList(),
                jailRoot = url,
                jsInterfaceObj = YouTubeSurgicalBridge(),
                jsInterfaceName = "AndroidBridge",
                jsInjector = { currentUrl ->
                    val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                    """
                    (function() {
                        $initSafeChannels
                        
                        if (window.gkMasterInterval) return;

                        window.gkMasterInterval = setInterval(function() {
                            var href = window.location.href || '';
                            // Use the bridge to log the current URL for debugging
                            if (window.AndroidBridge && window.AndroidBridge.logMessage) {
                                AndroidBridge.logMessage('Tick! href: ' + href);
                            }
                            
                            try {
                                var url = new URL(href);
                                var isWatchPage = url.pathname === '/watch';
                                var hasVideoParam = url.search.includes('v=');

                                // A true watch page has a '/watch' path and a 'v=' param.
                                // Search result transitions might briefly contain '/watch' but will resolve to '/results'.
                                if (isWatchPage && hasVideoParam) {
                                     if (window.AndroidBridge && window.AndroidBridge.logMessage) {
                                        AndroidBridge.logMessage('Blocking navigation to watch page: ' + href + '. Redirecting to subscriptions.');
                                     }
                                     window.location.href = 'https://m.youtube.com/feed/channels';
                                     return;
                                }
                            } catch (e) {
                                if (window.AndroidBridge && window.AndroidBridge.logMessage) {
                                    AndroidBridge.logMessage('URL parse error: ' + e.message + ' for href: ' + href);
                                }
                                // Fallback for safety, less precise.
                                if (href.includes('/watch?v=')) {
                                     if (window.AndroidBridge && window.AndroidBridge.logMessage) {
                                        AndroidBridge.logMessage('Blocking navigation via fallback due to URL parse error. Redirecting to subscriptions.');
                                     }
                                     window.location.href = 'https://m.youtube.com/feed/channels';
                                     return;
                                }
                            }
                            
                            if (href.includes('/feed/channels')) {
                                var channels = document.querySelectorAll('ytm-channel-renderer, ytm-compact-channel-renderer');
                                channels.forEach(function(channel) {
                                    if (!channel.querySelector('.gk-safe-toggle')) {
                                        var anchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"], a[href*="/c/"]');
                                        if (!anchor) return;
                                        var channelHref = anchor.getAttribute('href');
                                        var channelId = channelHref.split('/').pop();
                                        var channelName = channel.querySelector('.compact-media-item-headline, .channel-name')?.innerText || 'Unknown Channel';

                                        var isSafe = window.gkSafeChannels.includes(channelId);

                                        var btn = document.createElement('button');
                                        btn.className = 'gk-safe-toggle';
                                        btn.innerText = isSafe ? 'SAFE ✅' : 'SET SAFE';
                                        btn.style.backgroundColor = isSafe ? '#4CAF50' : '#444';
                                        btn.style.color = '#fff';
                                        btn.style.border = 'none';
                                        btn.style.padding = '8px 12px';
                                        btn.style.margin = '8px 0';
                                        btn.style.fontWeight = 'bold';
                                        btn.style.borderRadius = '4px';
                                        btn.style.width = '100%';
                                        
                                        btn.onclick = function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            AndroidBridge.toggleSafeChannel(channelId, channelName);
                                            var currentlySafe = window.gkSafeChannels.includes(channelId);
                                            if (currentlySafe) {
                                                window.gkSafeChannels = window.gkSafeChannels.filter(id => id !== channelId);
                                                btn.innerText = 'SET SAFE';
                                                btn.style.backgroundColor = '#444';
                                            } else {
                                                window.gkSafeChannels.push(channelId);
                                                btn.innerText = 'SAFE ✅';
                                                btn.style.backgroundColor = '#4CAF50';
                                            }
                                        };
                                        channel.appendChild(btn);
                                    }
                                });
                            }
                            
                            if (href.includes('/results') || href.includes('/channel/') || href.includes('/@') || href.includes('/c/')) {
                                document.body.classList.remove('gk-watch-page');
                                var styleId = 'gk-results-hide';
                                if (!document.getElementById(styleId)) {
                                    var style = document.createElement('style');
                                    style.id = styleId;
                                    style.textContent = `
                                        ytm-reel-shelf-renderer, 
                                        ytm-shorts-lockup-view-model, 
                                        ytm-shorts-lockup-view-model-v2, 
                                        yt-shorts-lockup-view-model, 
                                        ytm-rich-section-renderer, 
                                        .ytm-feed-filter-chip-bar-renderer { display: none !important; }
                                        
                                        /* Disable clicking on thumbnails and titles to prevent impulsive navigation */
                                        a[href*="/watch"] {
                                            pointer-events: none !important;
                                        }
                                        
                                        /* Re-enable events for our custom buttons */
                                        .gk-button-container {
                                            pointer-events: auto !important;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                }
                                
                                var videos = document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer, ytm-media-item-view-model, ytm-grid-video-renderer, ytm-item-section-renderer, ytm-playlist-video-renderer');
                                var channelNameHeader = document.querySelector('.c-channel-header-title, .ytm-channel-header-renderer-title, .channel-header-title')?.innerText || '';
                                
                                videos.forEach(function(video) {
                                    if (video.querySelector('a[href*="/shorts/"]') || video.querySelector('a[href*="/short/"]')) {
                                        video.style.setProperty('display', 'none', 'important');
                                        return;
                                    }
                                    if (!video.querySelector('.gk-button-container')) {
                                        var anchor = video.querySelector('a[href*="/watch"]');
                                        if (!anchor) return;
                                        var watchHref = anchor.getAttribute('href');
                                        var videoIdMatch = watchHref.match(/v=([^&]+)/);
                                        var videoId = videoIdMatch ? videoIdMatch[1] : null;
                                        if (!videoId) return;

                                        var title = video.querySelector('.compact-media-item-headline, .media-item-headline, h3, .ytm-media-item-metadata-title, .yt-core-attributed-string')?.innerText || 'Unknown Video';
                                        var channel = video.querySelector('.ytm-badge-and-byline-item-byline, ytm-badge-shape-renderer, .bylines, .ytm-media-item-inset-metadata-channel-title')?.innerText || channelNameHeader;
                                        var duration = video.querySelector('ytm-thumbnail-overlay-time-status-renderer, .yt-core-attributed-string[aria-label]')?.innerText || '';

                                        var btnContainer = document.createElement('div');
                                        btnContainer.className = 'gk-button-container';
                                        btnContainer.style.display = 'flex';
                                        btnContainer.style.flexDirection = 'row';
                                        btnContainer.style.gap = '8px';
                                        btnContainer.style.width = '100%';
                                        btnContainer.style.marginTop = '8px';

                                        var btn = document.createElement('button');
                                        btn.className = 'gk-save-btn';
                                        btn.innerText = '+ BANK';
                                        btn.style.flex = '1';
                                        btn.style.backgroundColor = '#4AF626';
                                        btn.style.color = '#000';
                                        btn.style.border = 'none';
                                        btn.style.padding = '12px 16px';
                                        btn.style.fontWeight = 'bold';
                                        btn.style.borderRadius = '4px';
                                        
                                        btn.onclick = function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            AndroidBridge.saveVideo(videoId, title, channel, duration);
                                            btn.innerText = 'SAVED ✓';
                                            btn.style.backgroundColor = '#888';
                                            btn.disabled = true;
                                        };
                                        btnContainer.appendChild(btn);
                                        video.appendChild(btnContainer);
                                    }
                                });

                                var channels = document.querySelectorAll('ytm-compact-channel-renderer');
                                channels.forEach(function(channel) {
                                    if (!channel.querySelector('.gk-channel-btn')) {
                                        var anchor = channel.querySelector('a');
                                        if (!anchor) return;
                                        var channelHref = anchor.getAttribute('href');
                                        
                                        var btn = document.createElement('button');
                                        btn.className = 'gk-channel-btn';
                                        btn.innerText = 'GO TO CHANNEL';
                                        btn.style.backgroundColor = '#FF9800';
                                        btn.style.color = '#000';
                                        btn.style.border = 'none';
                                        btn.style.padding = '12px 16px';
                                        btn.style.margin = '8px 0';
                                        btn.style.fontWeight = 'bold';
                                        btn.style.borderRadius = '4px';
                                        btn.style.width = '100%';
                                        btn.style.position = 'relative';
                                        btn.style.zIndex = '1000';
                                        btn.style.pointerEvents = 'auto';
                                        
                                        btn.onclick = function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            window.location.href = channelHref;
                                        };
                                        channel.appendChild(btn);
                                    }
                                });

                                var others = document.querySelectorAll('ytm-compact-playlist-renderer, ytm-compact-radio-renderer');
                                others.forEach(function(other) {
                                    if (!other.dataset.gkDisabled) {
                                        other.dataset.gkDisabled = 'true';
                                        other.style.opacity = '0.5';
                                        other.addEventListener('click', function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                        }, true);
                                    }
                                });
                            }
                        }, 1000);

                        // Stop YouTube SPA links escaping
                        if (!window.gkGlobalClickCatcher) {
                            window.gkGlobalClickCatcher = true;
                            document.addEventListener('click', function(e) {
                                const target = e.target;
                                if (!target) return;

                                const isGk = target.closest('.gk-save-btn, .gk-channel-btn, .gk-safe-toggle');
                                const isInput = target.closest('input, textarea, [contenteditable="true"], .searchbox-input');
                                const isTab = target.closest('[role="tab"], .ytm-tab-header-item, .tab-header-item');
                                const isHeader = target.closest('ytm-header-bar, ytm-search-header-renderer, .header-bar, .search-container');
                                const isIconOrBtn = target.closest('button,[role="button"], .searchbox-selection-cancel, svg, path');
                                
                                if (isGk || isInput || isTab || isHeader || isIconOrBtn) {
                                    return;
                                }

                                e.preventDefault();
                                e.stopPropagation();
                                
                                const anchor = target.closest('a');
                                if (anchor && anchor.href) {
                                    var newHref = anchor.href;
                                    if (newHref.includes('/channel/') || newHref.includes('/@') || newHref.includes('/c/')) {
                                        window.location.href = newHref;
                                    }
                                }
                            }, true);
                        }
                    })();
                    """.trimIndent()
                },
            )
        }
    }
}
