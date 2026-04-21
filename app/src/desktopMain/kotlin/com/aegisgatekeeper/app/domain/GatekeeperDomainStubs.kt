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
)

data class PinnedWebsite(
    val id: String,
    val label: String,
    val url: String,
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

    data class OpenPinnedWebsite(
        val url: String,
    ) : GatekeeperAction

    object ClosePinnedWebsite : GatekeeperAction
}

fun isVaultUnlocked(currentTime: LocalTime): Boolean {
    val start = LocalTime.of(18, 0)
    val end = LocalTime.of(18, 30)
    return !currentTime.isBefore(start) && currentTime.isBefore(end)
}
