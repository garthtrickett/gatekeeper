package com.aegisgatekeeper.app.domain

import java.util.UUID

data class DeletedAppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apps: Set<String> = emptySet(),
    val rules: List<BlockingRule> = emptyList(),
    val combinator: RuleCombinator = RuleCombinator.ANY,
)

interface DeletedBlockingRule

data class DeletedConsumedCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val timeMinutes: Int,
    val timestamp: Long,
)

data class DeletedTimeSlot(
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
)

/**
 * Represents a single query saved to the Vault instead of being searched immediately.
 */
data class DeletedVaultItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val capturedAtTimestamp: Long,
    val isResolved: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

/**
 * Represents a piece of curated media saved to the Content Bank.
 */
data class DeletedPodcastSubscription(
    val id: String = UUID.randomUUID().toString(),
    val feedUrl: String,
    val showTitle: String,
    val artworkUrl: String?,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class DeletedCachedEpisode(
    val id: String = UUID.randomUUID().toString(),
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

/**
 * Represents a piece of curated media saved to the Content Bank.
 */
data class DeletedContentItem(
    val id: String = UUID.randomUUID().toString(),
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
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val packageName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
)

/**
 * An immutable log of a completed session in a blacklisted app.
 */
data class DeletedMetacognitionRequest(
    val packageName: String,
    val durationMillis: Long,
)

data class DeletedSessionLog(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val durationMillis: Long,
    val emotion: Emotion,
    val loggedAtTimestamp: Long,
)

/**
 * Represents an active slot in the Intentional Dashboard.
 */
data class DeletedPinnedWebsite(
    val id: String,
    val label: String,
    val url: String,
)

data class DeletedIntentionalSlotItem(
    val slotIndex: Int,
    val contentItem: ContentItem,
)

data class DeletedAlternativeActivity(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
)

/**
 * Represents an active "Emergency Bypass" or completed Friction task.
 */
data class DeletedTemporaryWhitelist(
    val packageName: String,
    val reason: String?, // Null if granted via Friction task, String if Emergency Bypass
    val grantedAtTimestamp: Long,
    val expiresAtTimestamp: Long,
    val allocatedDurationMillis: Long,
)
