package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.api.YoutubeSearchItem

/**
 * The strict, immutable representation of the app's current state.
 * Always use .copy() to update values. Never use var.
 */
data class GatekeeperState(
    val appGroups: List<AppGroup> = emptyList(),
    // --- Active UI State ---
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    val activeForegroundApp: String? = null,
    val expiredSessionDurationMillis: Long? = null,
    val activeBlockReason: String? = null,
    // --- Dual-Moat Handoff State ---
    val isLayerOmegaActive: Boolean = false,
    // --- Subscription State ---
    val isProTier: Boolean = false,
    // --- Sync & Auth State ---
    val isAuthenticated: Boolean = false,
    val jwtToken: String? = null,
    // --- Business Logic State ---
    val isManualLockdownActive: Boolean = false,
    val missionControlApps: List<String> = emptyList(),
    val missionControlWebsites: List<PinnedWebsite> = emptyList(),
    val activeFrictionGame: FrictionGame = FrictionGame.GAUNTLET,
    val isProcessingLink: Boolean = false,
    val activeWhitelists: Map<String, TemporaryWhitelist> = emptyMap(),
    val customMessages: Map<String, String> = emptyMap(),
    val consumedCheckIns: List<ConsumedCheckIn> = emptyList(),
    val vaultItems: List<VaultItem> = emptyList(),
    val contentItems: List<ContentItem> = emptyList(),
    val activeContentFilter: ContentType? = null,
    val sessionLogs: List<SessionLog> = emptyList(),
    val intentionalSlots: List<IntentionalSlotItem> = emptyList(),
    val activeAudioUrl: String? = null,
    val analyticsBypasses: Int = 0,
    val analyticsGiveUps: Int = 0,
    val exportData: String? = null,
    val notificationDigest: List<NotificationLog> = emptyList(),
    val savedMediaPositions: Map<String, Float> = emptyMap(),
    // --- Clean Room Media Engine State ---
    val isLoadingYouTube: Boolean = false,
    val youtubeSearchResults: List<YoutubeSearchItem> = emptyList(),
    val activeVideoId: String? = null,
    // --- Surgical Web Engine State ---
    val isWebEngineReady: Boolean = false,
    val currentSurgicalUrl: String? = null,
    val activeFacebookUrl: String? = null,
    val activePinnedWebsiteUrl: String? = null,
    val syncServerUrl: String = "http://localhost:8081",
    // --- Permissions State (Dual-Moat Onboarding) ---
    val hasOverlayPermission: Boolean = false,
    val hasUsageAccessPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val isBatteryOptimizationDisabled: Boolean = false,
    val alternativeActivities: List<AlternativeActivity> = emptyList(),
) {
    val isDualMoatEnabled: Boolean
        get() = hasOverlayPermission && hasUsageAccessPermission && hasAccessibilityPermission && isBatteryOptimizationDisabled
}
