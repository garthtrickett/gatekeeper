package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
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
    private val parser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            val placeholderStreamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
            return item.copy(localFilePath = placeholderStreamUrl).right()
        }

        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.projectsegfau.lt",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.drgns.space"
        )

        for (instance in pipedInstances) {
            try {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Trying Piped ($instance)")
                val response = client.get("$instance/streams/${item.videoId}") {
                    timeout {
                        requestTimeoutMillis = 5000
                        connectTimeoutMillis = 5000
                    }
                }

                if (response.status.value in 200..299) {
                    val text = response.bodyAsText()
                    val pipedResponse = parser.decodeFromString(PipedResponse.serializer(), text)
                    
                    if (pipedResponse.error != null && pipedResponse.audioStreams.isNullOrEmpty()) continue

                    val stream = pipedResponse.audioStreams?.firstOrNull { it.format == "M4A" || it.mimeType?.contains("audio/mp4") == true }
                        ?: pipedResponse.audioStreams?.firstOrNull()

                    if (stream != null) {
                        com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Found Piped stream")
                        return item.copy(localFilePath = stream.url).right()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Piped instance failed: ${e.message}")
            }
        }

        val invidiousInstances = listOf(
            "https://invidious.lunar.icu",
            "https://inv.tux.pizza",
            "https://invidious.projectsegfau.lt",
            "https://iv.melmac.it"
        )

        for (instance in invidiousInstances) {
            try {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Trying Invidious ($instance)")
                val response = client.get("$instance/api/v1/videos/${item.videoId}") {
                    timeout {
                        requestTimeoutMillis = 5000
                        connectTimeoutMillis = 5000
                    }
                }

                if (response.status.value in 200..299) {
                    val text = response.bodyAsText()
                    val invResponse = parser.decodeFromString(InvidiousResponse.serializer(), text)
                    
                    val stream = invResponse.adaptiveFormats?.firstOrNull { it.type?.startsWith("audio/mp4") == true }
                        ?: invResponse.adaptiveFormats?.firstOrNull { it.type?.startsWith("audio/") == true }
                        ?: invResponse.formatStreams?.firstOrNull()

                    if (stream != null) {
                        com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Found Invidious stream")
                        return item.copy(localFilePath = stream.url).right()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Invidious instance failed: ${e.message}")
            }
        }

        return "Could not extract YouTube stream from any available instance".left()
    }
}
