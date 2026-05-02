package com.aegisgatekeeper.app.integrations

import arrow.core.Either
import com.aegisgatekeeper.app.domain.BeeperChat

interface BeeperClient {
    suspend fun getChats(): Either<String, List<BeeperChat>>

    suspend fun sendMessage(
        roomId: String,
        text: String,
    ): Either<String, Unit>
}
