package com.aegisgatekeeper.app.sync

import kotlinx.serialization.Serializable

sealed interface SyncError {
    object Unauthorized : SyncError
    data class NetworkFailure(val message: String) : SyncError
    data class ServerError(val code: Int) : SyncError
}

@Serializable
data class VaultItemDto(
    val id: String,
    val query: String,
    val capturedAtTimestamp: Long,
    val isResolved: Boolean,
    val lastModified: Long,
    val isDeleted: Boolean
)

@Serializable
data class ContentItemDto(
    val id: String,
    val videoId: String,
    val title: String,
    val source: String, // Enums become strings for serialization
    val type: String,
    val rank: Long,
    val capturedAtTimestamp: Long,
    val durationSeconds: Long?,
    val lastModified: Long,
    val isDeleted: Boolean
)

@Serializable
data class DeviceRegistrationRequest(
    val fcmToken: String
)

@Serializable
data class SyncPushPayload(
    val vaultItems: List<VaultItemDto>,
    val contentItems: List<ContentItemDto>
)

@Serializable
data class SyncPullPayload(
    val vaultItems: List<VaultItemDto>,
    val contentItems: List<ContentItemDto>,
    val serverTimestamp: Long
)
