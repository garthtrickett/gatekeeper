package com.aegisgatekeeper.app.effects

import arrow.core.Either
import arrow.core.left
import com.aegisgatekeeper.app.api.ContentMetadata
import com.aegisgatekeeper.app.api.PodcastFeedDto
import com.aegisgatekeeper.app.api.RssFeedData
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.ScheduledMessage
import com.aegisgatekeeper.app.domain.platformLog
import me.tatarka.inject.annotations.Inject

@Inject
class DesktopEffectHandler : PlatformEffectHandler {
    override fun goHome() {
        platformLog("Gatekeeper", "Desktop: Simulated goHome()")
    }

    override fun launchApp(packageName: String) {
        platformLog("Gatekeeper", "Desktop: Simulated launchApp($packageName)")
    }

    override fun triggerWidgetUpdate() {
        // No-op on desktop
    }

    override fun schedulePodcastRefresh() {
        platformLog("Gatekeeper", "Desktop: Simulated schedulePodcastRefresh()")
    }

    override fun scheduleBeeperMessage(
        message: ScheduledMessage,
        delayMillis: Long,
    ) {
        platformLog("Gatekeeper", "Desktop: Simulated scheduleBeeperMessage()")
    }

    override suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>> = "Not supported on desktop".left()

    override suspend fun fetchPodcastFeed(url: String): Either<String, RssFeedData> = "Not supported on desktop".left()

    override suspend fun fetchUrlMetadata(
        url: String,
        isSoundCloud: Boolean,
        isGeneric: Boolean,
    ): Either<String, ContentMetadata> = "Not supported on desktop".left()

    override suspend fun syncBeeperChats(): Either<String, List<BeeperChat>> = "Not supported on desktop".left()
}
