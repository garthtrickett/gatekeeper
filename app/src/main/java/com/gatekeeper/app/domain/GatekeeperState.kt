package com.gatekeeper.app.domain

import com.gatekeeper.app.api.YoutubeSearchItem

/**
 * The strict, immutable representation of the app's current state.
 * Always use .copy() to update values. Never use var.
 */
data class GatekeeperState(
    val blacklistedApps: Set<String> = emptySet(),
    // --- Active UI State ---
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    // --- Business Logic State ---
    val activeWhitelists: Map<String, TemporaryWhitelist> = emptyMap(),
    val vaultItems: List<VaultItem> = emptyList(),
    val contentItems: List<ContentItem> = emptyList(),
    val sessionLogs: List<SessionLog> = emptyList(),
    // --- Clean Room Media Engine State ---
    val isLoadingYouTube: Boolean = false,
    val youtubeSearchResults: List<YoutubeSearchItem> = emptyList(),
    val activeVideoId: String? = null,
)
