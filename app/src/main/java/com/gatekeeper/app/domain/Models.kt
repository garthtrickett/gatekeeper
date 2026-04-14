package com.gatekeeper.app.domain

import java.util.UUID

/**
 * Represents a single query saved to the Vault instead of being searched immediately.
 */
data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val capturedAtTimestamp: Long,
    val isResolved: Boolean = false,
)

/**
 * The core emotions for the Post-Session Metacognition pop-up.
 */
enum class Emotion {
    HAPPY,
    ANXIOUS,
    DRAINED,
    SKIPPED,
}

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
 * Represents an active "Emergency Bypass" or completed Friction task.
 */
data class TemporaryWhitelist(
    val packageName: String,
    val reason: String?, // Null if granted via Friction task, String if Emergency Bypass
    val grantedAtTimestamp: Long,
    val expiresAtTimestamp: Long,
)
