package com.aegisgatekeeper.app.domain

data class BeeperChat(
    val roomId: String,
    val name: String,
    val network: String?,
)

data class ScheduledMessage(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val beeperRoomId: String,
    val chatName: String,
    val messageText: String,
    val scheduledTimestamp: Long,
    val status: MessageStatus = MessageStatus.PENDING,
)
