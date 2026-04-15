package com.gatekeeper.app.domain

/**
 * A closed set of all possible intents or events in the system.
 */
sealed interface GatekeeperAction {
    // --- System Level OS Events ---
    data class AppBroughtToForeground(
        val packageName: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    object DismissOverlay : GatekeeperAction

    // --- Layer 1: Friction & Emergency Bypass ---
    data class FrictionCompleted(
        val packageName: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class EmergencyBypassRequested(
        val packageName: String,
        val reason: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class WhitelistExpired(
        val packageName: String,
    ) : GatekeeperAction

    // --- Layer 2: The Lookup Vault ---
    data class SaveToVault(
        val query: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class MarkVaultItemResolved(
        val id: String,
    ) : GatekeeperAction

    // --- Layer 3: The Content Bank ---
    data class ProcessSharedLink(
        val url: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class SaveToContentBank(
        val videoId: String,
        val title: String,
        val source: ContentSource,
        val type: ContentType,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class ReorderContentBank(
        val fromIndex: Int,
        val toIndex: Int,
    ) : GatekeeperAction

    data class RemoveFromContentBank(
        val id: String,
    ) : GatekeeperAction

    // --- Layer 5: Clean Room Media Engines ---
    data class SearchYouTubeRequested(
        val query: String,
    ) : GatekeeperAction

    data class YouTubeSearchCompleted(
        val results: List<com.gatekeeper.app.api.YoutubeSearchItem>,
    ) : GatekeeperAction

    object YouTubeSearchFailed : GatekeeperAction

    data class OpenCleanPlayer(
        val videoId: String,
    ) : GatekeeperAction

    object CloseCleanPlayer : GatekeeperAction

    // --- Post-Session Metacognition ---
    data class LogSessionMetacognition(
        val packageName: String,
        val durationMillis: Long,
        val emotion: Emotion,
        val currentTimestamp: Long,
    ) : GatekeeperAction
}
