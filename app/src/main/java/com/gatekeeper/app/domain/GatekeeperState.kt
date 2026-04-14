package com.gatekeeper.app.domain

/**
 * The strict, immutable representation of the app's current state.
 * Always use .copy() to update values. Never use var.
 */
data class GatekeeperState(
    val blacklistedApps: Set<String> = emptySet(),
    // --- Active UI State ---
    val isOverlayActive: Boolean = false,
    val currentlyInterceptedApp: String? = null,
    // --- Business Logic State ---
    val activeWhitelists: Map<String, TemporaryWhitelist> = emptyMap(),
    val vaultItems: List<VaultItem> = emptyList(),
    val sessionLogs: List<SessionLog> = emptyList(),
)
