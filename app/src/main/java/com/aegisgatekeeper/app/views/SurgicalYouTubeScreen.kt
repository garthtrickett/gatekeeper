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
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/results?search_query=")
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
                            root.querySelectorAll('*').forEach(el => {
                                if (el.shadowRoot) nuke(el.shadowRoot);
                            });
                        }
                        nuke(document);

                                                // 3. CHANNEL INTERFACE
                        if (href.includes('/feed/channels')) {
                            document.querySelectorAll('ytm-channel-renderer, ytm-compact-channel-renderer').forEach(function(channel) {
                                if (channel.dataset.gkHandled) return;

                                // Force navigation to the /videos tab instead of channel home
                                var subAnchors = channel.querySelectorAll('a[href*="/channel/"], a[href*="/@"]');
                                subAnchors.forEach(function(a) {
                                    var path = a.getAttribute('href');
                                    if (path && !path.includes('/videos') && !path.includes('/shorts') && !path.includes('/streams') && !path.includes('/community')) {
                                        a.href = path + (path.endsWith('/') ? '' : '/') + 'videos';
                                    }
                                });

                                var primaryAnchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"]');
                                if (!primaryAnchor) return;

                                channel.dataset.gkHandled = 'true';
                                var rawPath = primaryAnchor.getAttribute('href');
                                var channelId = '';
                                if (rawPath.includes('/channel/')) {
                                    channelId = rawPath.split('/channel/')[1].split('/')[0];
                                } else if (rawPath.includes('/@')) {
                                    channelId = '@' + rawPath.split('/@')[1].split('/')[0];
                                }
                                var isSafe = window.gkSafeChannels.includes(channelId);
                                var btn = document.createElement('button');
                                btn.innerText = isSafe ? 'SAFE ✅' : 'SET SAFE';
                                btn.style.cssText = 'background-color:' + (isSafe ? '#4CAF50' : '#444') + '; color:#fff; border:none; padding:8px 12px; margin:8px 0; font-weight:bold; border-radius:4px; width:100%;';
                                btn.onclick = function(e) {
                                    e.preventDefault(); e.stopPropagation();
                                    AndroidBridge.toggleSafeChannel(channelId, 'Channel');
                                };
                                channel.appendChild(btn);
                            });
                        }

                                                // 4. VIDEO RESULTS INTERFACE
                        if (href.includes('/results') || href.includes('/channel/') || href.includes('/@') || href.includes('/c/')) {
                            document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-media-item').forEach(function(video) {
                                if (video.dataset.gkHandled) return;
                                
                                var link = video.querySelector('a[data-gk-neutralized="true"]');
                                if (!link || !link.dataset.gkOriginalHref) return;
                                
                                var vIdMatch = link.dataset.gkOriginalHref.match(/v=([^&]+)/);
                                if (!vIdMatch) return;
                                var vId = vIdMatch[1];
                                
                                video.dataset.gkHandled = 'true';
                                
                                var titleEl = video.querySelector('h3.media-item-headline, h4.media-item-headline');
                                var title = titleEl ? titleEl.textContent.trim() : 'Video';
                                var channelEl = video.querySelector('ytm-badge-and-byline-renderer .yt-formatted-string');
                                var channelName = channelEl ? channelEl.textContent.trim() : 'Channel';
                                
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
