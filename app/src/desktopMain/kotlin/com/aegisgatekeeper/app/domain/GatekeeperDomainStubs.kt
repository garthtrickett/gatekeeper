package com.aegisgatekeeper.app.domain

import java.time.LocalTime

class DeletedGatekeeperState
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

data class DeletedAppGroup(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val name: String,
    val apps: Set<String> = emptySet(),
    val rules: List<BlockingRule> = emptyList(),
    val combinator: RuleCombinator = RuleCombinator.ANY,
)

interface DeletedBlockingRule

data class DeletedMetacognitionRequest(
    val packageName: String,
    val durationMillis: Long,
)

data class DeletedPodcastSubscription(
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

data class DeletedCachedEpisode(
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

data class DeletedUnifiedEpisode(
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

data class DeletedPinnedWebsite(
    val id: String,
    val label: String,
    val url: String,
)

data class DeletedAlternativeActivity(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val description: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
)

data class DeletedVaultItem(
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

data class DeletedContentItem(
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

data class DeletedNotificationLog(
    val id: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
)

interface DeletedGatekeeperAction

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

actual fun parseRssPubDate(
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
