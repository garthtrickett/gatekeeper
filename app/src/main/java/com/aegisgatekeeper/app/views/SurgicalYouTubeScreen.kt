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
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/feed/channels"),
                        )
                    },
                    text = "Subscriptions",
                    enabled = !url.contains("/feed/channels"),
                    invertEnabledColor = true,
                )
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

                        // 1. NEUTRALIZE LINKS: Kill navigation targets without blocking events.
                        // This ensures native browser scrolling is NEVER interrupted by preventDefault().
                        document.querySelectorAll('a[href*="/watch?v="], a[href*="/shorts/"]').forEach(function(link) {
                            if (link.dataset.gkNeutralized) return;
                            
                            // Save original URL so the +BANK button can still find the video ID
                            link.dataset.gkOriginalHref = link.href;
                            link.href = 'javascript:void(0);';
                            link.dataset.gkNeutralized = 'true';
                            
                            // Stop standard navigation click behavior just in case
                            link.onclick = function(e) {
                                e.stopPropagation();
                                if (typeof AndroidBridge !== 'undefined') {
                                    AndroidBridge.logMessage('🛡️ Neutralized Link Tapped: ' + link.dataset.gkOriginalHref);
                                }
                                return false;
                            };
                        });

                        // 2. COSMETIC NUKE: Hide algorithmic distraction shelves
                        const selectors = ['ytm-reel-shelf-renderer', 'ytm-pivot-bar-item-renderer[tab-id="FEshorts"]', 'a[href^="/shorts/"]', '.reel-shelf-header-view-model-wiz'];
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
                                var anchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"]');
                                if (!anchor) return;
                                channel.dataset.gkHandled = 'true';
                                var channelId = anchor.getAttribute('href').split('/').pop();
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
                        if (href.includes('/results') || href.includes('/channel/') || href.includes('/@')) {
                            document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer').forEach(function(video) {
                                if (video.dataset.gkHandled) return;
                                
                                // Find the link (which we neutralized in step 1)
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
                                
                                // THE FIX: Robust duration extraction
                                // 1. Try standard selectors
                                var durationEl = video.querySelector('ytm-thumbnail-overlay-time-status-renderer span, .ytm-thumbnail-overlay-time-status-renderer, .badge-shape-wiz__text');
                                var duration = durationEl ? durationEl.textContent.trim() : '';
                                
                                // 2. Fallback: Search the entire card text for a timestamp pattern (e.g. 04:20 or 1:02:30)
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

                        // 5. DISABLE AUTOPLAY: Find and remove video tags and preview players
                        // We nuke the video elements so they can't play, and remove the preview overlay components.
                        document.querySelectorAll('video, ytm-inline-preview-player-renderer').forEach(function(el) {
                            el.remove();
                        });
                    }

                    // Use MutationObserver to neutralize links as they load into the DOM
                    window.gkObserver = new MutationObserver(applyFilters);
                    window.gkObserver.observe(document.body, { childList: true, subtree: true });
                    
                    // Run once immediately to catch existing content
                    applyFilters();
                })();
                """.trimIndent()
            },
        )
    }
}


