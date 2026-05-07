package com.aegisgatekeeper.app.domain

data class AppGroup(
    val id: String = randomUUIDString(),
    val name: String,
    val apps: Set<String> = emptySet(),
    val rules: List<BlockingRule> = emptyList(),
    val combinator: RuleCombinator = RuleCombinator.ANY,
)

sealed interface BlockingRule {
    val id: String
    val groupId: String
    val isEnabled: Boolean

    data class TimeLimit(
        override val id: String = randomUUIDString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val timeLimitMinutes: Int,
    ) : BlockingRule

    data class ScheduledBlock(
        override val id: String = randomUUIDString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val timeSlots: List<TimeSlot>,
        val daysOfWeek: Set<DayOfWeek>,
    ) : BlockingRule

    data class CheckIn(
        override val id: String = randomUUIDString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int = 15,
        val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    ) : BlockingRule

    data class DomainBlock(
        override val id: String = randomUUIDString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val domains: Set<String>,
    ) : BlockingRule

    data class AlwaysBlock(
        override val id: String = randomUUIDString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
    ) : BlockingRule
}

data class ConsumedCheckIn(
    val id: String = randomUUIDString(),
    val groupId: String,
    val timeMinutes: Int,
    val timestamp: Long,
)

data class TimeSlot(
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
)

data class VaultItem(
    val id: String = randomUUIDString(),
    val query: String,
    val capturedAtTimestamp: Long,
    val isResolved: Boolean = false,
    val lastModified: Long = currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class PodcastSubscription(
    val id: String = randomUUIDString(),
    val feedUrl: String,
    val showTitle: String,
    val artworkUrl: String?,
    val lastModified: Long = currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class CachedEpisode(
    val id: String = randomUUIDString(),
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
    val lastModified: Long = currentTimeMillis(),
)

data class UnifiedEpisode(
    val id: String,
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
    val lastModified: Long,
    val showTitle: String,
    val artworkUrl: String?,
)

data class ContentItem(
    val id: String = randomUUIDString(),
    val podcastId: String? = null,
    val videoId: String,
    val title: String,
    val channelName: String? = null,
    val source: ContentSource,
    val type: ContentType,
    val rank: Long,
    val capturedAtTimestamp: Long,
    val durationSeconds: Long? = null,
    val lastModified: Long = currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val localFilePath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
)

data class NotificationLog(
    val id: String = randomUUIDString(),
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
)

data class MetacognitionRequest(
    val packageName: String,
    val durationMillis: Long,
)

data class SessionLog(
    val id: String = randomUUIDString(),
    val packageName: String,
    val durationMillis: Long,
    val emotion: Emotion,
    val loggedAtTimestamp: Long,
)

data class PinnedWebsite(
    val id: String,
    val label: String,
    val url: String,
)

data class IntentionalSlotItem(
    val slotIndex: Int,
    val contentItem: ContentItem,
)

data class AlternativeActivity(
    val id: String = randomUUIDString(),
    val description: String,
    val createdAtTimestamp: Long = currentTimeMillis(),
)

data class TemporaryWhitelist(
    val packageName: String,
    val reason: String?,
    val grantedAtTimestamp: Long,
    val expiresAtTimestamp: Long,
    val allocatedDurationMillis: Long,
)

data class InterceptionState(
    val appGroups: List<AppGroup> = emptyList(),
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    val activeForegroundApp: String? = null,
    val expiredSessionDurationMillis: Long? = null,
    val activeBlockReason: String? = null,
    val pendingExitInterview: String? = null,
    val isLayerOmegaActive: Boolean = false,
    val isManualLockdownActive: Boolean = false,
    val activeFrictionGame: FrictionGame = FrictionGame.GAUNTLET,
    val activeWhitelists: Map<String, TemporaryWhitelist> = emptyMap(),
    val hasOverlayPermission: Boolean = false,
    val hasUsageAccessPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasNotificationAccessPermission: Boolean = false,
    val isBatteryOptimizationDisabled: Boolean = false,
) {
    val isDualMoatEnabled: Boolean
        get() =
            hasOverlayPermission && hasUsageAccessPermission && hasAccessibilityPermission && hasNotificationAccessPermission &&
                isBatteryOptimizationDisabled
}

data class DataState(
    val deepWorkStartMinutes: Int = 540,
    val deepWorkEndMinutes: Int = 1020,
    val gatheringStartMinutes: Int = 1080,
    val gatheringEndMinutes: Int = 1110,
    val missionControlApps: List<String> = emptyList(),
    val missionControlWebsites: List<PinnedWebsite> = emptyList(),
    val customMessages: Map<String, String> = emptyMap(),
    val consumedCheckIns: List<ConsumedCheckIn> = emptyList(),
    val vaultItems: List<VaultItem> = emptyList(),
    val contentItems: List<ContentItem> = emptyList(),
    val intentionalSlots: List<IntentionalSlotItem> = emptyList(),
    val alternativeActivities: List<AlternativeActivity> = emptyList(),
    val sessionLogs: List<SessionLog> = emptyList(),
    val analyticsBypasses: Int = 0,
    val analyticsGiveUps: Int = 0,
    val exportData: String? = null,
    val pendingMetacognition: MetacognitionRequest? = null,
)

data class MediaState(
    val isProcessingLink: Boolean = false,
    val isSearchingPodcasts: Boolean = false,
    val podcastSearchResults: List<com.aegisgatekeeper.app.api.PodcastFeedDto> = emptyList(),
    val activePodcastEpisodes: List<CachedEpisode>? = null,
    val activePodcastId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val latestGlobalEpisodes: List<UnifiedEpisode>? = null,
    val isLoadingGlobalEpisodes: Boolean = false,
    val activeDownloads: Map<String, Float> = emptyMap(),
    val savedMediaPositions: Map<String, Float> = emptyMap(),
    val activeNativeMediaItem: ContentItem? = null,
    val isNativePlayerMaximized: Boolean = false,
    val isWebEngineReady: Boolean = false,
    val currentSurgicalUrl: String? = null,
    val extractingMediaId: String? = null,
    val activeFacebookUrl: String? = null,
    val activeYouTubeUrl: String? = null,
    val activePinnedWebsiteUrl: String? = null,
    val filterRulesHash: String? = null,
    val declarativeFilterRules: List<String> =
        listOf(
            "||google-analytics.com^",
            "||doubleclick.net^",
            "||connect.facebook.net^",
            "||ads.twitter.com^",
            "||googletagmanager.com^",
            "twitter.com,x.com##[data-testid='sidebarColumn']",
            "twitter.com,x.com##[data-testid='primaryColumn'] > div > div:nth-child(2)",
            "twitter.com,x.com##nav[aria-label='Primary'] > a:nth-child(2)",
            "twitter.com,x.com##nav[aria-label='Primary'] > a:nth-child(5)",
            "substack.com##.feed-container",
            "substack.com##.top-posts-container",
            "substack.com##.sidebar",
            "youtube.com###secondary",
            "youtube.com###related",
            "youtube.com##ytd-reel-shelf-renderer",
            "youtube.com##ytd-shorts",
        ),
)

data class SyncAndIntegrationState(
    val isProTier: Boolean = false,
    val isAuthenticated: Boolean = false,
    val jwtToken: String? = null,
    val syncServerUrl: String = "http://localhost:8081",
    val podcastSubscriptions: List<PodcastSubscription> = emptyList(),
    val isSyncingPodcasts: Boolean = false,
    val podcastSyncError: String? = null,
    val notificationDigest: List<NotificationLog> = emptyList(),
    val beeperChats: List<BeeperChat> = emptyList(),
    val scheduledMessages: List<ScheduledMessage> = emptyList(),
    val isSyncingBeeper: Boolean = false,
)

data class RuleEvaluationSnapshot(
    val targetPackage: String,
    val activeGroups: List<AppGroup>,
    val isManualLockdownActive: Boolean,
    val currentMinutes: Int,
    val currentDay: DayOfWeek,
    val usageStats: Map<String, Int>,
)

sealed interface EvaluationVerdict {
    object Allowed : EvaluationVerdict

    data class Blocked(
        val reason: String,
    ) : EvaluationVerdict
}
