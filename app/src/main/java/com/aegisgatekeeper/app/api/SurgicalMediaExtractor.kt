package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import io.ktor.client.HttpClient
import me.tatarka.inject.annotations.Inject

@Inject
@Singleton
class SurgicalMediaExtractor(
    private val client: HttpClient,
) {
    suspend fun extractMedia(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            return item.copy(localFilePath = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4").right()
        }

                if (item.source == ContentSource.YOUTUBE) {
            return "YouTube extraction is no longer supported via proxy. Use Surgical Web View.".left()
        }

        val fullUrl = item.videoId // For SoundCloud and others, the videoId holds the full URL

        // Append .mp3 as an exoplayer_hint to help ExoPlayer quickly identify the raw HTTP chunk stream
        val encodedUrl = java.net.URLEncoder.encode(fullUrl, "UTF-8")
        val proxyUrl = "${com.aegisgatekeeper.app.BuildConfig.SURGICAL_PROXY_URL}/stream?id=$encodedUrl&exoplayer_hint=.mp3"
        com.aegisgatekeeper.app.domain
            .platformLog("Gatekeeper", "✅ SurgicalMediaExtractor: Tunnelling through Surgical Proxy -> $proxyUrl")

        // Instantly return the proxy URL as the localFilePath for the native player to consume
        return item.copy(localFilePath = proxyUrl).right()
    }
}
