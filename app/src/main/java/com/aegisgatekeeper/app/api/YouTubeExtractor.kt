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
    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        // Simulated stream URL (ExoPlayer Test Media)
        val placeholderStreamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

        return item.copy(localFilePath = placeholderStreamUrl).right()
    }
}
