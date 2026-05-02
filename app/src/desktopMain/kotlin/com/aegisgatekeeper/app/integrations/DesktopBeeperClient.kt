package com.aegisgatekeeper.app.integrations

import arrow.core.Either
import arrow.core.left
import com.aegisgatekeeper.app.domain.BeeperChat
import me.tatarka.inject.annotations.Inject

@Inject
class DesktopBeeperClient : BeeperClient {
    override suspend fun getChats(): Either<String, List<BeeperChat>> = "Beeper integration is not available on Desktop".left()

    override suspend fun sendMessage(
        roomId: String,
        text: String,
    ): Either<String, Unit> = "Beeper integration is not available on Desktop".left()
}
