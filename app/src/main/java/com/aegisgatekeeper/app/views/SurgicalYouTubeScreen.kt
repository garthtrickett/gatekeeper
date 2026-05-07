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
@SuppressLint("SetJavaScriptEnabled")
@Suppress("FunctionName")
@Composable
fun SurgicalYouTubeScreen(
    url: String,
    onClose: () -> Unit,
) {
    val state by GatekeeperStateManager.state.collectAsState()
    val safeChannelIds = remember(state.data.safeYouTubeChannels) {
        state.data.safeYouTubeChannels.keys.joinToString(",") { "'$it'" }
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
                            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/feed/channels"),
                        )
                    },
                    text = "Subscriptions",
                    enabled = !url.contains("/feed/channels"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/results?search_query="),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/results?search_query="),
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
                filterRules = listOf(
                    SurgicalFilterRule(
                        urlCondition = { true },
                        hiddenSelectors = listOf(
                            "ytm-header-bar", 
                            "ytm-pivot-bar-renderer", 
                            "ytm-search-header-renderer", 
                            ".modern-sharing-ui"
                        )
                    )
                ),
                networkBlocklist = emptyList(),
                jailRoot = url,
                jsInterfaceObj = YouTubeSurgicalBridge(),
                jsInterfaceName = "AndroidBridge",
                jsInjector = { currentUrl ->
                    val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                    when {
                        currentUrl.contains("/feed/channels") -> {
                            """
                            (function() {
                                $initSafeChannels
                                var checkInterval = setInterval(function() {
                                    var channels = document.querySelectorAll('ytm-channel-renderer, ytm-compact-channel-renderer');
                                    channels.forEach(function(channel) {
                                        if (!channel.querySelector('.gk-safe-toggle')) {
                                            var anchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"]');
                                            if (!anchor) return;
                                            var href = anchor.getAttribute('href');
                                            var channelId = href.split('/').pop();
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
                                }, 1000);
                            })();
                            """.trimIndent()
                        }
                        currentUrl.contains("/watch") -> {
                            """
                            (function() {
                                document.body.classList.add('gk-watch-page');
                                var styleId = 'gk-watch-hide';
                                if (!document.getElementById(styleId)) {
                                    var style = document.createElement('style');
                                    style.id = styleId;
                                    style.textContent = '.gk-watch-page ytm-item-section-renderer, .gk-watch-page ytm-comment-section-renderer, .gk-watch-page ytm-rich-grid-renderer, .gk-watch-page ytm-metadata-row-container-renderer { display: none !important; }';
                                    document.head.appendChild(style);
                                }

                                var checkInterval = setInterval(function() {
                                    var btnContainer = document.querySelector('ytm-slim-video-action-bar-renderer') || document.querySelector('.slim-video-action-bar-actions');
                                    if (btnContainer && !document.getElementById('gk-save-btn')) {
                                        var btn = document.createElement('button');
                                        btn.id = 'gk-save-btn';
                                        btn.innerText = '+ SAVE TO BANK';
                                        btn.style.backgroundColor = '#4AF626';
                                        btn.style.color = '#000';
                                        btn.style.border = 'none';
                                        btn.style.padding = '8px 16px';
                                        btn.style.margin = '8px';
                                        btn.style.fontWeight = 'bold';
                                        btn.style.borderRadius = '4px';
                                        btn.style.width = 'calc(100% - 16px)';
                                        btn.onclick = function() {
                                            var videoId = new URLSearchParams(window.location.search).get('v');
                                            var title = document.querySelector('.slim-video-metadata-title')?.textContent || document.title;
                                            var channel = document.querySelector('ytm-badge-shape-renderer')?.textContent || document.querySelector('.slim-owner-channel-name')?.textContent || '';
                                            var duration = document.querySelector('.time-display-content')?.textContent || '';
                                            AndroidBridge.saveVideo(videoId, title, channel, duration);
                                            btn.innerText = 'SAVED ✓';
                                            btn.style.backgroundColor = '#888';
                                        };
                                        btnContainer.parentNode.insertBefore(btn, btnContainer);
                                    }
                                }, 1000);
                            })();
                            """.trimIndent()
                        }
                        currentUrl.contains("/results") -> {
                            """
                            (function() {
                                document.body.classList.remove('gk-watch-page');
                                var styleId = 'gk-results-hide';
                                if (!document.getElementById(styleId)) {
                                    var style = document.createElement('style');
                                    style.id = styleId;
                                    style.textContent = 'ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytm-shorts-lockup-view-model-v2, yt-shorts-lockup-view-model, ytm-rich-section-renderer { display: none !important; }';
                                    document.head.appendChild(style);
                                }
                                var checkInterval = setInterval(function() {
                                    var videos = document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer');
                                    videos.forEach(function(video) {
                                        if (video.querySelector('a[href*="/shorts/"]') || video.querySelector('a[href*="/short/"]')) {
                                            video.style.setProperty('display', 'none', 'important');
                                            return;
                                        }
                                        if (!video.querySelector('.gk-save-btn')) {
                                            var anchor = video.querySelector('a[href*="/watch"]');
                                            if (!anchor) return;
                                            var href = anchor.getAttribute('href');
                                            var videoIdMatch = href.match(/v=([^&]+)/);
                                            var videoId = videoIdMatch ? videoIdMatch[1] : null;
                                            if (!videoId) return;

                                            var title = video.querySelector('.compact-media-item-headline, .media-item-headline, h3')?.innerText || 'Unknown Video';
                                            var channel = video.querySelector('.ytm-badge-and-byline-item-byline, ytm-badge-shape-renderer, .bylines')?.innerText || '';
                                            var duration = video.querySelector('ytm-thumbnail-overlay-time-status-renderer')?.innerText || '';

                                            var btn = document.createElement('button');
                                            btn.className = 'gk-save-btn';
                                            btn.innerText = '+ SAVE TO BANK';
                                            btn.style.backgroundColor = '#4AF626';
                                            btn.style.color = '#000';
                                            btn.style.border = 'none';
                                            btn.style.padding = '12px 16px';
                                            btn.style.margin = '8px 0';
                                            btn.style.fontWeight = 'bold';
                                            btn.style.borderRadius = '4px';
                                            btn.style.width = '100%';
                                            
                                            btn.onclick = function(e) {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                AndroidBridge.saveVideo(videoId, title, channel, duration);
                                                btn.innerText = 'SAVED ✓';
                                                btn.style.backgroundColor = '#888';
                                            };
                                            video.appendChild(btn);
                                        }
                                    });

                                    var channels = document.querySelectorAll('ytm-compact-channel-renderer');
                                    channels.forEach(function(channel) {
                                        if (!channel.querySelector('.gk-channel-btn')) {
                                            var anchor = channel.querySelector('a');
                                            if (!anchor) return;
                                            var href = anchor.getAttribute('href');
                                            
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
                                                window.location.href = href;
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
                                }, 1000);
                            })();
                            """.trimIndent()
                        }
                        else -> "document.body.classList.remove('gk-watch-page');"
                    }
                },
            )
        }
    }
}
    url: String,
    onClose: () -> Unit,
) {
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
                            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/feed/channels"),
                        )
                    },
                    text = "Subscriptions",
                    enabled = !url.contains("/feed/channels"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.OpenSurgicalYouTube("https://m.youtube.com/results?search_query="),
                        )
                    },
                    text = "Search",
                    enabled = !url.contains("/results?search_query="),
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
                                if (result == "\"clicked\"") {
                                    android.util.Log.d("Gatekeeper", "✨ SPA hack succeeded. Soft navigating to: ${'$'}newUrl")
                                } else {
                                    android.util.Log.d("Gatekeeper", "🛡️ SPA hack failed (${'$'}result). Falling back to hard load: ${'$'}newUrl")
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
                                filterRules = listOf(
                    SurgicalFilterRule(
                        urlCondition = { true },
                        hiddenSelectors = listOf(
                            "ytm-header-bar", // Top bar with logo/search
                            "ytm-pivot-bar-renderer", // Bottom bar with Home/Shorts/You
                            "ytm-search-header-renderer", // Search bar header
                            ".modern-sharing-ui" // Share popups
                        )
                    )
                ),
                networkBlocklist = emptyList(),
                jailRoot = url,
                jsInterfaceObj = YouTubeSurgicalBridge(),
                jsInterfaceName = "AndroidBridge",
                jsInjector = { currentUrl ->
                    val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                                        if (currentUrl.contains("/feed/channels")) {
                        """
                        (function() {
                            $initSafeChannels
                            var checkInterval = setInterval(function() {
                                var channels = document.querySelectorAll('ytm-channel-renderer, ytm-compact-channel-renderer');
                                channels.forEach(function(channel) {
                                    if (!channel.querySelector('.gk-safe-toggle')) {
                                        var anchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"]');
                                        if (!anchor) return;
                                        var href = anchor.getAttribute('href');
                                        var channelId = href.split('/').pop();
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
                                        
                                        btn.onclick = function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            AndroidBridge.toggleSafeChannel(channelId, channelName);
                                            // Optimistic UI update
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
                            }, 1000);
                        })();
                        """.trimIndent()
                    } else 
                        """
                        (function() {
                            var checkInterval = setInterval(function() {
                                var items = document.querySelectorAll('ytm-item-section-renderer, ytm-video-with-context-renderer');
                                items.forEach(function(item) {
                                    if (!item.querySelector('.gk-save-btn')) {
                                        var anchor = item.querySelector('a[href*="/watch"]');
                                        if (!anchor) return;
                                        var href = anchor.getAttribute('href');
                                        var videoIdMatch = href.match(/v=([^&]+)/);
                                        var videoId = videoIdMatch ? videoIdMatch[1] : null;
                                        if (!videoId) return;

                                        var title = item.querySelector('.compact-media-item-headline, h3')?.innerText || 'Unknown Video';
                                        var channel = item.querySelector('.ytm-badge-and-byline-item-byline')?.innerText || '';
                                        var duration = item.querySelector('ytm-thumbnail-overlay-time-status-renderer')?.innerText || '';

                                        var btn = document.createElement('button');
                                        btn.className = 'gk-save-btn';
                                        btn.innerText = '+ SAVE TO BANK';
                                        btn.style.backgroundColor = '#4AF626';
                                        btn.style.color = '#000';
                                        btn.style.border = 'none';
                                        btn.style.padding = '12px 16px';
                                        btn.style.margin = '8px 0';
                                        btn.style.fontWeight = 'bold';
                                        btn.style.borderRadius = '4px';
                                        btn.style.width = '100%';
                                        
                                        btn.onclick = function(e) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            AndroidBridge.saveVideo(videoId, title, channel, duration);
                                            btn.innerText = 'SAVED ✓';
                                            btn.style.backgroundColor = '#888';
                                        };
                                        item.appendChild(btn);
                                    }
                                });
                            }, 1000);
                        })();
                        """.trimIndent()
                    } else if (currentUrl.contains("/watch")) {
                        """
                        (function() {
                            document.body.classList.add('gk-watch-page');
                            var styleId = 'gk-watch-hide';
                            if (!document.getElementById(styleId)) {
                                var style = document.createElement('style');
                                style.id = styleId;
                                style.textContent = '.gk-watch-page ytm-item-section-renderer, .gk-watch-page ytm-comment-section-renderer, .gk-watch-page ytm-rich-grid-renderer, .gk-watch-page ytm-metadata-row-container-renderer { display: none !important; }';
                                document.head.appendChild(style);
                            }

                            var checkInterval = setInterval(function() {
                                var btnContainer = document.querySelector('ytm-slim-video-action-bar-renderer') || document.querySelector('.slim-video-action-bar-actions');
                                if (btnContainer && !document.getElementById('gk-save-btn')) {
                                    var btn = document.createElement('button');
                                    btn.id = 'gk-save-btn';
                                    btn.innerText = '+ SAVE TO BANK';
                                    btn.style.backgroundColor = '#4AF626';
                                    btn.style.color = '#000';
                                    btn.style.border = 'none';
                                    btn.style.padding = '8px 16px';
                                    btn.style.margin = '8px';
                                    btn.style.fontWeight = 'bold';
                                    btn.style.borderRadius = '4px';
                                    btn.style.width = 'calc(100% - 16px)';
                                    btn.onclick = function() {
                                        var videoId = new URLSearchParams(window.location.search).get('v');
                                        var title = document.querySelector('.slim-video-metadata-title')?.textContent || document.title;
                                        var channel = document.querySelector('ytm-badge-shape-renderer')?.textContent || document.querySelector('.slim-owner-channel-name')?.textContent || '';
                                        var duration = document.querySelector('.time-display-content')?.textContent || '';
                                        AndroidBridge.saveVideo(videoId, title, channel, duration);
                                        btn.innerText = 'SAVED ✓';
                                        btn.style.backgroundColor = '#888';
                                    };
                                    btnContainer.parentNode.insertBefore(btn, btnContainer);
                                    clearInterval(checkInterval);
                                }
                                                        }, 1000);
                        })();
                        """.trimIndent()
                                        } else if (currentUrl.contains("/results")) {
                        """
                        (function() {
                            document.body.classList.remove('gk-watch-page');
                            var styleId = 'gk-results-hide';
                            if (!document.getElementById(styleId)) {
                                var style = document.createElement('style');
                                style.id = styleId;
                                style.textContent = 'ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytm-shorts-lockup-view-model-v2, yt-shorts-lockup-view-model, ytm-rich-section-renderer { display: none !important; }';
                                document.head.appendChild(style);
                            }
                                                        console.log('🔍 JS: Initializing Search Result Interceptor...');
                                                        var checkInterval = setInterval(function() {
                                var videos = document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer');
                                if (videos.length > 0 && Math.random() < 0.1) {
                                     console.log('🔍 JS: Processing ' + videos.length + ' video elements...');
                                }
                                videos.forEach(function(video) {
                                    if (video.querySelector('a[href*="/shorts/"]') || video.querySelector('a[href*="/short/"]')) {
                                        video.style.setProperty('display', 'none', 'important');
                                        return;
                                    }
                                    if (!video.querySelector('.gk-save-btn')) {
                                        var anchor = video.querySelector('a[href*="/watch"]');
                                        if (!anchor) return;
                                        var href = anchor.getAttribute('href');
                                        var videoIdMatch = href.match(/v=([^&]+)/);
                                        var videoId = videoIdMatch ? videoIdMatch[1] : null;
                                        if (!videoId) return;

                                        var title = video.querySelector('.compact-media-item-headline, .media-item-headline, h3')?.innerText || 'Unknown Video';
                                        var channel = video.querySelector('.ytm-badge-and-byline-item-byline, ytm-badge-shape-renderer, .bylines')?.innerText || '';
                                        var duration = video.querySelector('ytm-thumbnail-overlay-time-status-renderer')?.innerText || '';

                                        var btn = document.createElement('button');
                                        btn.className = 'gk-save-btn';
                                        btn.innerText = '+ SAVE TO BANK';
                                        btn.style.backgroundColor = '#4AF626';
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
                                            AndroidBridge.saveVideo(videoId, title, channel, duration);
                                            btn.innerText = 'SAVED ✓';
                                            btn.style.backgroundColor = '#888';
                                        };
                                        
                                                                                                                        console.log('➕ JS: Attached Save button to video: ' + videoId);
                                        video.appendChild(btn);
                                    }
                                });

                                var channels = document.querySelectorAll('ytm-compact-channel-renderer');
                                channels.forEach(function(channel) {
                                    if (!channel.querySelector('.gk-channel-btn')) {
                                        var anchor = channel.querySelector('a');
                                        if (!anchor) return;
                                        var href = anchor.getAttribute('href');
                                        
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
                                            window.location.href = href;
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
                            }, 1000);
                        })();
                        """.trimIndent()
                    } else {
                        "document.body.classList.remove('gk-watch-page');"
                    }
                },
            )
        }
    }
}
