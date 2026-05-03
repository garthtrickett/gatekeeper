package com.aegisgatekeeper.app.domain

/**
 * The strict, immutable representation of the app's current state.
 * Always use .copy() to update values. Never use var.
 */
data class GatekeeperState(
    val interception: InterceptionState = InterceptionState(),
    val data: DataState = DataState(),
    val media: MediaState = MediaState(),
    val sync: SyncAndIntegrationState = SyncAndIntegrationState(),
) {
    val isDualMoatEnabled: Boolean
        get() = interception.isDualMoatEnabled
}
    val appGroups: List<AppGroup> = emptyList(),
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    val activeForegroundApp: String? = null,
    val expiredSessionDurationMillis: Long? = null,
    val activeBlockReason: String? = null,
    val pendingExitInterview: String? = null,
    val isLayerOmegaActive: Boolean = false,
    val isProTier: Boolean = false,
    val isAuthenticated: Boolean = false,
    val jwtToken: String? = null,
    val isManualLockdownActive: Boolean = false,
    val deepWorkStartMinutes: Int = 540,
    val deepWorkEndMinutes: Int = 1020,
    val gatheringStartMinutes: Int = 1080,
    val gatheringEndMinutes: Int = 1110,
    val missionControlApps: List<String> = emptyList(),
    val missionControlWebsites: List<PinnedWebsite> = emptyList(),
    val activeFrictionGame: FrictionGame = FrictionGame.GAUNTLET,
    val isProcessingLink: Boolean = false,
    val activeWhitelists: Map<String, TemporaryWhitelist> = emptyMap(),
    val customMessages: Map<String, String> = emptyMap(),
    val consumedCheckIns: List<ConsumedCheckIn> = emptyList(),
    val podcastSubscriptions: List<PodcastSubscription> = emptyList(),
    val vaultItems: List<VaultItem> = emptyList(),
    val contentItems: List<ContentItem> = emptyList(),
    val isSyncingPodcasts: Boolean = false,
    val podcastSyncError: String? = null,
    val isSearchingPodcasts: Boolean = false,
    val podcastSearchResults: List<com.aegisgatekeeper.app.api.PodcastFeedDto> = emptyList(),
    val activePodcastEpisodes: List<CachedEpisode>? = null,
    val activePodcastId: String? = null,
    val isLoadingEpisodes: Boolean = false,
        val latestGlobalEpisodes: List<UnifiedEpisode>? = null,
    val isLoadingGlobalEpisodes: Boolean = false,
    val activeDownloads: Map<String, Float> = emptyMap(),
    val sessionLogs: List<SessionLog> = emptyList(),
    val intentionalSlots: List<IntentionalSlotItem> = emptyList(),
    val activeAudioUrl: String? = null,
    val analyticsBypasses: Int = 0,
    val analyticsGiveUps: Int = 0,
    val exportData: String? = null,
    val notificationDigest: List<NotificationLog> = emptyList(),
    val savedMediaPositions: Map<String, Float> = emptyMap(),
    val pendingMetacognition: MetacognitionRequest? = null,
    val activeVideoId: String? = null,
        val beeperChats: List<BeeperChat> = emptyList(),
    val scheduledMessages: List<ScheduledMessage> = emptyList(),
    val isSyncingBeeper: Boolean = false,
    val activeNativeMediaItem: ContentItem? = null,
    val isWebEngineReady: Boolean = false,
    val currentSurgicalUrl: String? = null,
    val activeFacebookUrl: String? = null,
    val activePinnedWebsiteUrl: String? = null,
    val syncServerUrl: String = "http://localhost:8081",
    val hasOverlayPermission: Boolean = false,
    val hasUsageAccessPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasNotificationAccessPermission: Boolean = false,
    val isBatteryOptimizationDisabled: Boolean = false,
    val alternativeActivities: List<AlternativeActivity> = emptyList(),
) {
    val isDualMoatEnabled: Boolean
        get() =
            hasOverlayPermission && hasUsageAccessPermission && hasAccessibilityPermission && hasNotificationAccessPermission &&
                isBatteryOptimizationDisabled
}
