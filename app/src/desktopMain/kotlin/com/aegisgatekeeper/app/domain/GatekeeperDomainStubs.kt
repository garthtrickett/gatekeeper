package com.aegisgatekeeper.app.domain

import java.time.LocalTime

data class GatekeeperState(
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    val notificationDigest: List<NotificationLog> = emptyList(),
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
    val alternativeActivities: List<AlternativeActivity> = emptyList(),
    val podcastSubscriptions: List<PodcastSubscription> = emptyList(),
    val pendingMetacognition: MetacognitionRequest? = null,
    val isSyncingPodcasts: Boolean = false,
    val podcastSyncError: String? = null,
    val activePodcastEpisodes: List<CachedEpisode>? = null,
    val activePodcastId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val activeDownloads: Map<String, Float> = emptyMap(),
    val latestGlobalEpisodes: List<UnifiedEpisode>? = null,
    val isLoadingGlobalEpisodes: Boolean = false,
)

data class MetacognitionRequest(
    val packageName: String,
    val durationMillis: Long,
)

data class PodcastSubscription(
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

data class CachedEpisode(
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

data class PinnedWebsite(
    val id: String,
    val label: String,
    val url: String,
)

data class AlternativeActivity(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val description: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
)

data class VaultItem(
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

data class ContentItem(
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

data class NotificationLog(
    val id: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
)

sealed interface GatekeeperAction {
    object DismissOverlay : GatekeeperAction

    object ClearNotificationDigest : GatekeeperAction

    object WebEngineInitialized : GatekeeperAction

    data class SurgicalNavigationRequested(
        val url: String,
    ) : GatekeeperAction

    data class SurgicalNavigationCompleted(
        val url: String,
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
}

fun isVaultUnlocked(currentTime: LocalTime): Boolean {
    val start = LocalTime.of(18, 0)
    val end = LocalTime.of(18, 30)
    return !currentTime.isBefore(start) && currentTime.isBefore(end)
}

fun parseRssPubDate(
    dateStr: String?,
    fallback: Long,
): Long {
    if (dateStr.isNullOrBlank()) return fallback
    val cleanDate = dateStr.trim().replace(Regex("\\s+"), " ")
    
    try {
        return java.time.Instant.parse(cleanDate).toEpochMilli()
    } catch (e: Exception) {}
    
    val zonedFormatters = listOf(
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
        java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME,
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", java.util.Locale.US),
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.US)
    )

    for (formatter in zonedFormatters) {
        try {
            return java.time.ZonedDateTime.parse(cleanDate, formatter).toInstant().toEpochMilli()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    val localFormatters = listOf(
        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    )
    for (formatter in localFormatters) {
        try {
            return java.time.LocalDateTime.parse(cleanDate, formatter)
                .atZone(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {}
    }

    try {
        return java.time.LocalDate.parse(cleanDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(java.time.ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        // Ignore
    }

    return fallback
}
