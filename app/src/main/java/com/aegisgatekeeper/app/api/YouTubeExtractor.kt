package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject

@Serializable
data class PipedAudioStream(
    val url: String,
    val format: String? = null,
    val quality: String? = null,
    val mimeType: String? = null
)

@Serializable
data class PipedResponse(
    val audioStreams: List<PipedAudioStream>? = null,
    val error: String? = null
)

@Serializable
data class InvidiousFormat(
    val url: String,
    val type: String? = null
)

@Serializable
data class InvidiousResponse(
    val adaptiveFormats: List<InvidiousFormat>? = null,
    val formatStreams: List<InvidiousFormat>? = null
)

@Inject
@Singleton
class YouTubeExtractor(private val client: HttpClient) {
    private val parser = Json { ignoreUnknownKeys = true }

    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        // Fallback for isolated UI tests so network requests don't break mock environments
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            val placeholderStreamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
            return item.copy(localFilePath = placeholderStreamUrl).right()
        }

        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://piped-api.garudalinux.org",
            "https://api.piped.projectsegfau.lt"
        )

        for (instance in pipedInstances) {
            try {
                val response = client.get("$instance/streams/${item.videoId}")
                if (response.status.value in 200..299) {
                    val text = response.bodyAsText()
                    val pipedResponse = parser.decodeFromString(PipedResponse.serializer(), text)
                    
                    if (pipedResponse.error != null && pipedResponse.audioStreams.isNullOrEmpty()) {
                        continue
                    }

                    // Prefer M4A streams as they play flawlessly in ExoPlayer without video tracks attached
                    val stream = pipedResponse.audioStreams?.firstOrNull { it.format == "M4A" }
                        ?: pipedResponse.audioStreams?.firstOrNull()

                    if (stream != null) {
                        return item.copy(localFilePath = stream.url).right()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                continue
            }
        }

        val invidiousInstances = listOf(
            "https://inv.tux.pizza",
            "https://invidious.jing.rocks",
            "https://vid.puffyan.us"
        )

        for (instance in invidiousInstances) {
            try {
                val response = client.get("$instance/api/v1/videos/${item.videoId}")
                if (response.status.value in 200..299) {
                    val text = response.bodyAsText()
                    val invResponse = parser.decodeFromString(InvidiousResponse.serializer(), text)
                    
                    val stream = invResponse.adaptiveFormats?.firstOrNull { it.type?.startsWith("audio/mp4") == true }
                        ?: invResponse.adaptiveFormats?.firstOrNull { it.type?.startsWith("audio/") == true }
                        ?: invResponse.formatStreams?.firstOrNull()

                    if (stream != null) {
                        return item.copy(localFilePath = stream.url).right()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                continue
            }
        }

        return "Could not extract YouTube stream from any available instance".left()
    }
}
