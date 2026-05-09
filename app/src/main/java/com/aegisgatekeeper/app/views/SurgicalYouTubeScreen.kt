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
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/feed/channels")
                        )
                    },
                    text = "Subscriptions",
                    enabled = !url.contains("/feed/channels"),
                    invertEnabledColor = true,
                )
                IndustrialButton(
                    onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/results?search_query=podcasts")
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

        BaseSurgicalWebView(
            url = url,
            modifier = Modifier.weight(1f),
            onUrlChangeRequested = { requestedUrl ->
                // Native Interception: Treat the navigation as an intent to change state
                GatekeeperStateManager.dispatch(GatekeeperAction.SurgicalNavigationRequested(requestedUrl))
            },
            filterRules = listOf(
                SurgicalFilterRule(
                    urlCondition = { true },
                    hiddenSelectors = listOf(
                        "ytm-pivot-bar-renderer",
                        ".modern-sharing-ui",
                    ),
                ),
            ),
            networkBlocklist = emptyList(),
            jailRoot = url,
            jsInterfaceObj = YouTubeSurgicalBridge(),
            jsInterfaceName = "AndroidBridge",
            jsInjector = { _ ->
                val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                """
                (function() {
                    $initSafeChannels
                    
                    if (window.gkCosmeticInterval) {
                        clearInterval(window.gkCosmeticInterval);
                    }

                    // This interval is now strictly for COSMETIC UI updates (injecting buttons).
                    // Navigation logic is handled natively by shouldOverrideUrlLoading.
                    window.gkCosmeticInterval = setInterval(function() {
                        var href = window.location.href || '';
                        if (!href || href === 'about:blank') return;

                        // 1. SAFE CHANNEL TOGGLES (Subscriptions Page)
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
                                    btn.style.cssText = 'background-color:' + (isSafe ? '#4CAF50' : '#444') + '; color:#fff; border:none; padding:8px 12px; margin:8px 0; font-weight:bold; border-radius:4px; width:100%;';
                                    
                                    btn.onclick = function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        AndroidBridge.toggleSafeChannel(channelId, channelName);
                                    };
                                    channel.appendChild(btn);
                                }
                            });
                        }
                        
                        // 2. SURGICAL BUTTONS (Search Results / Channel Pages)
                        if (href.includes('/results') || href.includes('/channel/') || href.includes('/@') || href.includes('/c/')) {
                            var videos = document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer, ytm-media-item-view-model, ytm-grid-video-renderer, ytm-item-section-renderer, ytm-playlist-video-renderer');
                            videos.forEach(function(video) {
                                // Block shorts visually immediately
                                if (video.querySelector('a[href*="/shorts/"]')) {
                                    video.style.display = 'none';
                                    return;
                                }
                                
                                if (!video.querySelector('.gk-button-container')) {
                                    var anchor = video.querySelector('a[href*="/watch"]');
                                    if (!anchor) return;
                                    var watchHref = anchor.getAttribute('href');
                                    var videoIdMatch = watchHref.match(/v=([^&]+)/);
                                    var videoId = videoIdMatch ? videoIdMatch[1] : null;
                                    if (!videoId) return;

                                    var title = video.querySelector('.compact-media-item-headline, h3, .ytm-media-item-metadata-title')?.innerText || 'Unknown Video';
                                    var channel = video.querySelector('.ytm-badge-and-byline-item-byline, .bylines')?.innerText || '';
                                    var duration = video.querySelector('ytm-thumbnail-overlay-time-status-renderer')?.innerText || '';

                                    var container = document.createElement('div');
                                    container.className = 'gk-button-container';
                                    container.style.marginTop = '8px';

                                    var btn = document.createElement('button');
                                    btn.innerText = '+ BANK';
                                    btn.style.cssText = 'background-color:#4AF626; color:#000; border:none; padding:12px 16px; font-weight:bold; border-radius:4px; width:100%;';
                                    
                                    btn.onclick = function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        AndroidBridge.saveVideo(videoId, title, channel, duration);
                                        btn.innerText = 'SAVED ✓';
                                        btn.style.backgroundColor = '#888';
                                    };
                                    container.appendChild(btn);
                                    video.appendChild(container);
                                }
                            });
                        }
                    }, 1000);

                    // 3. GLOBAL CLICK CATCHER (Stop impulsive escapism)
                    if (!window.gkGlobalClickCatcher) {
                        window.gkGlobalClickCatcher = true;
                        document.addEventListener('click', function(e) {
                            const target = e.target;
                            if (!target) return;

                            // Allow surgical buttons and inputs
                            const isGk = target.closest('.gk-save-btn, .gk-safe-toggle, .gk-button-container, button');
                            const isInput = target.closest('input, textarea, [contenteditable="true"]');
                            if (isGk || isInput) return;

                            // For everything else, prevent the default behavior.
                            // The native shouldOverrideUrlLoading will handle state transitions for links.
                            const anchor = target.closest('a');
                            if (anchor) {
                                // We let the event happen so the native side can catch the URL intent,
                                // but we block the SPA from doing a "soft" navigation internally.
                                android.util.Log.d("Gatekeeper.SurgicalJS", "Link clicked: " + anchor.href);
                            }
                        }, true);
                    }
                })();
                """.trimIndent()
            }
        )
    }
}
