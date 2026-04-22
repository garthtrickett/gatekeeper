package com.aegisgatekeeper.app.domain

import java.util.UUID

data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
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
        override val id: String = UUID.randomUUID().toString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val timeLimitMinutes: Int,
    ) : BlockingRule

    data class ScheduledBlock(
        override val id: String = UUID.randomUUID().toString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val timeSlots: List<TimeSlot>,
        val daysOfWeek: Set<DayOfWeek>,
    ) : BlockingRule

    data class CheckIn(
        override val id: String = UUID.randomUUID().toString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int = 15,
        val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    ) : BlockingRule

    data class DomainBlock(
        override val id: String = UUID.randomUUID().toString(),
        override val groupId: String,
        override val isEnabled: Boolean = true,
        val domains: Set<String>,
    ) : BlockingRule
}

data class ConsumedCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val timeMinutes: Int,
    val timestamp: Long,
)

data class TimeSlot(
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
)

/**
 * Represents a single query saved to the Vault instead of being searched immediately.
 */
data class VaultItem(
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
data class ContentItem(
    val id: String = UUID.randomUUID().toString(),
    val videoId: String,
    val title: String,
    val source: ContentSource,
    val type: ContentType,
    val rank: Long,
    val capturedAtTimestamp: Long,
    val durationSeconds: Long? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)

data class NotificationLog(
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
data class SessionLog(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val durationMillis: Long,
    val emotion: Emotion,
    val loggedAtTimestamp: Long,
)

/**
 * Represents an active slot in the Intentional Dashboard.
 */
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
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
)

/**
 * Represents an active "Emergency Bypass" or completed Friction task.
 */
data class TemporaryWhitelist(
    val packageName: String,
    val reason: String?, // Null if granted via Friction task, String if Emergency Bypass
    val grantedAtTimestamp: Long,
    val expiresAtTimestamp: Long,
    val allocatedDurationMillis: Long,
)
