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
