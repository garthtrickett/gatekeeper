package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.ContentItem
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject



@Inject
@Singleton
class YouTubeExtractor(private val client: HttpClient) {
    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            return item.copy(localFilePath = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4").right()
        }

        // Append .mp3 as an exoplayer_hint to help ExoPlayer quickly identify the raw HTTP chunk stream
        val proxyUrl = "${com.aegisgatekeeper.app.BuildConfig.SURGICAL_PROXY_URL}/stream?id=${item.videoId}&exoplayer_hint=.mp3"
        com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Tunnelling through Surgical Proxy -> $proxyUrl")
        
        // Instantly return the proxy URL as the localFilePath for the native player to consume
        return item.copy(localFilePath = proxyUrl).right()
    }
}
