package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import me.tatarka.inject.annotations.Inject

@Inject
@Singleton
class YouTubeExtractor {
        suspend fun extractVideo(videoId: String): Either<String, ContentItem> {
        // Simulated stream URL (Big Buck Bunny)
        val placeholderStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        val item = ContentItem(
            videoId = videoId,
            title = "Placeholder Video Title",
            channelName = "Placeholder Channel",
            source = ContentSource.YOUTUBE,
            type = ContentType.VIDEO,
            rank = -1,
            capturedAtTimestamp = System.currentTimeMillis(),
            durationSeconds = 596,
        ).copy(localFilePath = placeholderStreamUrl) // Use localFilePath to carry the stream URL

        return item.right()
    }
}