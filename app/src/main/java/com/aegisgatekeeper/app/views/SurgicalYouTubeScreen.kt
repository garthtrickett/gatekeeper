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
                            GatekeeperAction.SurgicalNavigationRequested("https://m.youtube.com/results?search_query=podcasts"),
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
            jsInjector = { _ ->
                val initSafeChannels = "window.gkSafeChannels = [$safeChannelIds];"
                """
                (function() {
                    $initSafeChannels
                    if (window.gkInterval) clearInterval(window.gkInterval);
                    window.gkInterval = setInterval(function() {
                        var href = window.location.href;
                        if (!href || href === 'about:blank') return;
                        if (href.includes('/feed/channels')) {
                            document.querySelectorAll('ytm-channel-renderer, ytm-compact-channel-renderer').forEach(function(channel) {
                                if (channel.querySelector('.gk-safe-toggle')) return;
                                var anchor = channel.querySelector('a[href*="/channel/"], a[href*="/@"]');
                                if (!anchor) return;
                                var channelId = anchor.getAttribute('href').split('/').pop();
                                var isSafe = window.gkSafeChannels.includes(channelId);
                                var btn = document.createElement('button');
                                btn.className = 'gk-safe-toggle';
                                btn.innerText = isSafe ? 'SAFE ✅' : 'SET SAFE';
                                btn.style.cssText = 'background-color:' + (isSafe ? '#4CAF50' : '#444') + '; color:#fff; border:none; padding:8px 12px; margin:8px 0; font-weight:bold; border-radius:4px; width:100%;';
                                btn.onclick = function(e) {
                                    e.preventDefault(); e.stopPropagation();
                                    AndroidBridge.toggleSafeChannel(channelId, 'Channel');
                                };
                                channel.appendChild(btn);
                            });
                        }
                        if (href.includes('/results') || href.includes('/channel/') || href.includes('/@')) {
                            document.querySelectorAll('ytm-compact-video-renderer, ytm-video-with-context-renderer').forEach(function(video) {
                                var anchors = video.querySelectorAll('a[href*="/watch"]');
                    if (anchors.length === 0) return;

                    // Disable navigation on ALL links leading to a watch page
                    anchors.forEach(function(anchor) {
                        anchor.onclick = function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            return false;
                        };
                    });

                    // If we've already added our button, don't do it again
                    if (video.querySelector('.gk-btn')) return;

                    // Use the first anchor to get the video ID for banking
                    var vId = anchors[0].href.match(/v=([^&]+)/)?.[1];
                    if (!vId) return;

                    // Extract real metadata
                    var titleEl = video.querySelector('h3.media-item-headline, h4.media-item-headline');
                    var title = titleEl ? titleEl.textContent.trim() : 'Video';
                    var channelEl = video.querySelector('ytm-badge-and-byline-renderer .yt-formatted-string');
                                        var channelName = channelEl ? channelEl.textContent.trim() : 'Channel';
                    var durationEl = video.querySelector('ytm-thumbnail-overlay-time-status-renderer span');
                    var duration = durationEl ? durationEl.textContent.trim() : '';

                    var container = document.createElement('div');
                    container.className = 'gk-btn';
                    var btn = document.createElement('button');
                    btn.innerText = '+ BANK';
                    btn.style.cssText = 'background-color:#4AF626; color:#000; border:none; padding:12px 16px; font-weight:bold; border-radius:4px; width:100%; margin-top:8px;';
                    btn.onclick = function(e) {
                        e.preventDefault(); e.stopPropagation();
                        AndroidBridge.saveVideo(vId, title, channelName, duration);
                        btn.innerText = 'SAVED ✓'; btn.style.backgroundColor = '#888';
                    };
                    container.appendChild(btn);

                    video.appendChild(container);
                            });
                        }
                    }, 1000);
                })();
                """.trimIndent()
            },
        )
    }
}
