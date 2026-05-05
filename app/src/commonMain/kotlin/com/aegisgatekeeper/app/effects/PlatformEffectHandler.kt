package com.aegisgatekeeper.app.effects

import arrow.core.Either
import com.aegisgatekeeper.app.api.ContentMetadata
import com.aegisgatekeeper.app.api.PodcastFeedDto
import com.aegisgatekeeper.app.api.RssFeedData
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.ScheduledMessage

interface PlatformEffectHandler {
    fun goHome()

    fun launchApp(packageName: String)

    fun triggerWidgetUpdate()

    fun schedulePodcastRefresh()

    fun scheduleBeeperMessage(
        message: ScheduledMessage,
        delayMillis: Long,
    )

    suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>>

    suspend fun fetchPodcastFeed(url: String): Either<String, RssFeedData>

    suspend fun fetchUrlMetadata(
        url: String,
        isSoundCloud: Boolean = false,
        isGeneric: Boolean = false,
    ): Either<String, ContentMetadata>

    suspend fun syncBeeperChats(): Either<String, List<BeeperChat>>

    suspend fun fetchYouTubeStream(videoId: String): Either<String, com.aegisgatekeeper.app.domain.ContentItem>
}
