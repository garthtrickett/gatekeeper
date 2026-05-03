package com.aegisgatekeeper.app.domain

import java.time.LocalTime

data class GatekeeperState_Deleted(
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    val notificationDigest: List<NotificationLog> = emptyList(),
    val appGroups: List<AppGroup> = emptyList(),
    val isWebEngineReady: Boolean = false,
    val isAuthenticated: Boolean = false,
    val jwtToken: String? = null,
    val currentSurgicalUrl: String? = null,
    val syncServerUrl: String = "http://localhost:8081",
    val vaultItems: List<VaultItem> = emptyList(),
    val contentItems: List<ContentItem> = emptyList(),
    val savedMediaPositions: Map<String, Float> = emptyMap(),
    val activePinnedWebsiteUrl: String? = null,
    val missionControlWebsites: List<PinnedWebsite> = emptyList(),
    val deepWorkStartMinutes: Int = 540,
    val deepWorkEndMinutes: Int = 1020,
    val gatheringStartMinutes: Int = 1080,
    val gatheringEndMinutes: Int = 1110,
    val alternativeActivities: List<AlternativeActivity> = emptyList(),
    val podcastSubscriptions: List<PodcastSubscription> = emptyList(),
    val pendingMetacognition: MetacognitionRequest? = null,
    val isSyncingPodcasts: Boolean = false,
    val podcastSyncError: String? = null,
    val activePodcastEpisodes: List<CachedEpisode>? = null,
    val activePodcastId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val activeDownloads: Map<String, Float> = emptyMap(),
    val beeperChats: List<BeeperChat> = emptyList(),
    val scheduledMessages: List<ScheduledMessage> = emptyList(),
    val isSyncingBeeper: Boolean = false,
    val latestGlobalEpisodes: List<UnifiedEpisode>? = null,
    val isLoadingGlobalEpisodes: Boolean = false,
)

data class AppGroup_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val name: String,
    val apps: Set<String> = emptySet(),
    val rules: List<BlockingRule> = emptyList(),
    val combinator: RuleCombinator = RuleCombinator.ANY,
)

sealed interface BlockingRule_Deleted {
    val id: String
    val groupId: String
    val isEnabled: Boolean

    data class CheckIn(
        override val id: String =
            java.util.UUID
                .randomUUID()
                .toString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int = 15,
        val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    ) : BlockingRule
}

data class MetacognitionRequest_Deleted(
    val packageName: String,
    val durationMillis: Long,
)

data class PodcastSubscription_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val feedUrl: String,
    val showTitle: String,
    val artworkUrl: String?,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class CachedEpisode_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
    val lastModified: Long = System.currentTimeMillis(),
)

data class UnifiedEpisode_Deleted(
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

data class PinnedWebsite_Deleted(
    val id: String,
    val label: String,
    val url: String,
)

data class AlternativeActivity_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val description: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
)

data class VaultItem_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val query: String,
    val capturedAtTimestamp: Long,
    val isResolved: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class ContentItem_Deleted(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val podcastId: String? = null,
    val videoId: String,
    val title: String,
    val channelName: String? = null,
    val source: ContentSource,
    val type: ContentType,
    val rank: Long,
    val capturedAtTimestamp: Long,
    val durationSeconds: Long? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val localFilePath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
)

data class NotificationLog_Deleted(
    val id: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
)

sealed interface GatekeeperAction_Deleted {
    object DismissOverlay : GatekeeperAction

    object ClearNotificationDigest : GatekeeperAction

    object WebEngineInitialized : GatekeeperAction

    data class SurgicalNavigationRequested(
        val url: String,
    ) : GatekeeperAction

    data class SurgicalNavigationCompleted(
        val url: String,
    ) : GatekeeperAction

    data class SetManualLockdown(
        val isActive: Boolean,
    ) : GatekeeperAction

    data class UpdatePhaseWindows(
        val deepWorkStartMinutes: Int,
        val deepWorkEndMinutes: Int,
        val gatheringStartMinutes: Int,
        val gatheringEndMinutes: Int,
    ) : GatekeeperAction

    data class UpdateMissionControlApps(
        val token: String,
    ) : GatekeeperAction

    data class RequestMagicLink(
        val email: String,
    ) : GatekeeperAction

    data class LoginSuccess(
        val token: String,
    ) : GatekeeperAction

    object Logout : GatekeeperAction

    data class UpdateSyncUrl(
        val url: String,
    ) : GatekeeperAction

    data class RemoteSyncCompleted(
        val newVaultItems: List<VaultItem>,
        val newContentItems: List<ContentItem>,
    ) : GatekeeperAction

    data class ProcessPodcastUrl(
        val url: String,
    ) : GatekeeperAction

    data class SavePodcastSubscription(
        val subscription: PodcastSubscription,
    ) : GatekeeperAction

    data class RemovePodcastSubscription(
        val id: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    object RefreshAllFeedsRequested : GatekeeperAction

    object PodcastSyncStarted : GatekeeperAction

    object PodcastSyncCompleted : GatekeeperAction

    data class PodcastSyncFailed(
        val error: String,
    ) : GatekeeperAction

    object ClearPodcastSyncError : GatekeeperAction

    data class LoadPodcastEpisodes(
        val feedUrl: String,
        val podcastId: String,
    ) : GatekeeperAction

    data class CacheParsedEpisodes(
        val episodes: List<com.aegisgatekeeper.app.api.RssEpisode>,
        val podcastId: String,
    ) : GatekeeperAction

    data class PodcastEpisodesLoaded(
        val episodes: List<CachedEpisode>,
        val podcastId: String,
    ) : GatekeeperAction

    object ClearPodcastEpisodes : GatekeeperAction

    object LoadLatestGlobalEpisodes : GatekeeperAction

    data class LatestGlobalEpisodesLoaded(
        val episodes: List<UnifiedEpisode>,
    ) : GatekeeperAction

    data class AddEpisodeToBank(
        val episode: CachedEpisode,
        val podcastId: String,
        val podcastTitle: String,
    ) : GatekeeperAction

    data class OpenNativePlayer(
        val contentItem: ContentItem,
    ) : GatekeeperAction

    object CloseNativePlayer : GatekeeperAction

    data class SaveMediaPosition(
        val mediaId: String,
        val positionSeconds: Float,
    ) : GatekeeperAction

    data class AddPinnedWebsite(
        val id: String,
        val label: String,
        val url: String,
    ) : GatekeeperAction

    data class RemovePinnedWebsite(
        val id: String,
    ) : GatekeeperAction

    data class AddAlternativeActivity(
        val description: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class RemoveAlternativeActivity(
        val id: String,
    ) : GatekeeperAction

    data class TriggerMetacognition(
        val packageName: String,
        val durationMillis: Long,
    ) : GatekeeperAction

    object ClearMetacognition : GatekeeperAction

    data class OpenPinnedWebsite(
        val url: String,
    ) : GatekeeperAction

    object ClosePinnedWebsite : GatekeeperAction

    data class DownloadMediaRequested(
        val id: String,
    ) : GatekeeperAction

    data class DownloadProgressUpdated(
        val id: String,
        val progress: Float,
    ) : GatekeeperAction

    data class DownloadCompleted(
        val id: String,
        val localFilePath: String,
    ) : GatekeeperAction

    data class DownloadFailed(
        val id: String,
    ) : GatekeeperAction

    data class DeleteDownloadedMedia(
        val id: String,
    ) : GatekeeperAction

    object RequestBeeperSync : GatekeeperAction

    data class BeeperChatsLoaded(
        val chats: List<BeeperChat>,
    ) : GatekeeperAction

    data class BeeperSyncFailed(
        val error: String,
    ) : GatekeeperAction

    data class ScheduleMessage(
        val message: ScheduledMessage,
    ) : GatekeeperAction

    data class CancelScheduledMessage(
        val id: String,
    ) : GatekeeperAction

    data class MessageDelivered(
        val id: String,
    ) : GatekeeperAction

    data class MessageFailed(
        val id: String,
        val error: String,
    ) : GatekeeperAction
}

fun isDeepWorkHours(
    currentTime: LocalTime,
    startMinutes: Int,
    endMinutes: Int,
): Boolean {
    val current = currentTime.hour * 60 + currentTime.minute
    return if (startMinutes <= endMinutes) {
        current in startMinutes until endMinutes
    } else {
        current >= startMinutes || current < endMinutes
    }
}

fun getNextDeliveryTime(
    currentMinutes: Int,
    currentDay: DayOfWeek,
    rule: BlockingRule.CheckIn,
): Int? = null

fun isMailDelivered(
    notificationTimestamp: Long,
    currentTimeMillis: Long,
    rule: BlockingRule.CheckIn,
    currentDay: DayOfWeek,
): Boolean = true

fun isVaultUnlocked(
    currentTime: LocalTime,
    startMinutes: Int,
    endMinutes: Int,
): Boolean {
    val current = currentTime.hour * 60 + currentTime.minute
    return if (startMinutes <= endMinutes) {
        current in startMinutes until endMinutes
    } else {
        current >= startMinutes || current < endMinutes
    }
}

fun formatMinutesToAmPm(minutes: Int): String {
    var h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    h %= 12
    if (h == 0) h = 12
    return String.format("%d:%02d %s", h, m, ampm)
}

fun parseRssPubDate(
    dateStr: String?,
    fallback: Long,
): Long {
    if (dateStr.isNullOrBlank()) return fallback
    val cleanDate = dateStr.trim().replace(Regex("\\s+"), " ")

    try {
        return java.time.Instant
            .parse(cleanDate)
            .toEpochMilli()
    } catch (e: Exception) {
    }

    val zonedFormatters =
        listOf(
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
            java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME,
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ssX", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.US),
        )

    for (formatter in zonedFormatters) {
        try {
            return java.time.ZonedDateTime
                .parse(cleanDate, formatter)
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            // Ignore
        }
    }

    val localFormatters =
        listOf(
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
        )
    for (formatter in localFormatters) {
        try {
            return java.time.LocalDateTime
                .parse(cleanDate, formatter)
                .atZone(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
        }
    }

    try {
        return java.time.LocalDate
            .parse(cleanDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(java.time.ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        // Ignore
    }

    return fallback
}
