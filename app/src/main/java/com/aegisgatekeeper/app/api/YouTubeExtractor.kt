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

@Serializable
data class CobaltRequest(
    val url: String,
    val videoQuality: String = "720",
    val downloadMode: String = "audio",
    val audioFormat: String = "mp3",
    val isAudioOnly: Boolean = true
)

@Serializable
data class CobaltResponse(
    val status: String? = null,
    val url: String? = null,
    val text: String? = null
)

@Inject
@Singleton
class YouTubeExtractor(private val client: HttpClient) {
    private val parser = Json { ignoreUnknownKeys = true }

    private suspend fun tryCobalt(endpoint: String, videoId: String): String? {
        try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Connecting to Local Cobalt ($endpoint)")
            val response = client.post(endpoint) {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("Accept", "application/json")
                setBody(CobaltRequest(url = "https://www.youtube.com/watch?v=$videoId"))
                timeout {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 10000
                }
            }

            val responseBody = response.bodyAsText()
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "🔍 Cobalt Raw: $responseBody")

            if (response.status.value in 200..299) {
                val cobalt = parser.decodeFromString(CobaltResponse.serializer(), responseBody)
                if (cobalt.url != null) {
                    var finalUrl = cobalt.url
                    // If Cobalt returns a relative path (e.g. /api/stream...), prefix it with the endpoint
                    if (finalUrl.startsWith("/")) {
                        finalUrl = "${endpoint.trimEnd('/')}$finalUrl"
                    }
                    
                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Stream Resolved -> $finalUrl")
                    return finalUrl
                }
            }
        } catch (e: Exception) {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Connection failed to $endpoint")
        }
        return null
    }

    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            return item.copy(localFilePath = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4").right()
        }

        // For Physical Device + ADB Reverse, we only care about localhost.
        // We try 127.0.0.1 as well because some Android versions handle it better than 'localhost'.
        val localEndpoints = listOf(
            "http://127.0.0.1:9099/",
            "http://localhost:9099/"
        )

        for (endpoint in localEndpoints) {
            val url = tryCobalt(endpoint, item.videoId)
            if (url != null) return item.copy(localFilePath = url).right()
        }

        return "Extraction Failed: Cobalt is unreachable. Run 'adb reverse tcp:9099 tcp:9099'".left()
    }
}
