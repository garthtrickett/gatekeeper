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
    val audioFormat: String = "best",
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
        val requestUrl = "https://www.youtube.com/watch?v=$videoId"
        try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Connecting to Local Cobalt ($endpoint) for URL: $requestUrl")
            val response = client.post(endpoint) {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("Accept", "application/json")
                setBody(CobaltRequest(url = requestUrl))
                                timeout {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 2000
                }
            }

            val responseBody = response.bodyAsText()
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "🔍 Cobalt Response Code: ${response.status.value}")
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "🔍 Cobalt Raw: $responseBody")

            if (response.status.value in 200..299) {
                val cobalt = parser.decodeFromString(CobaltResponse.serializer(), responseBody)
                if (cobalt.url != null) {
                    var finalUrl = cobalt.url
                    // If Cobalt returns a relative path (e.g. /api/stream...), prefix it with the endpoint
                    if (finalUrl.startsWith("/")) {
                        finalUrl = "${endpoint.trimEnd('/')}$finalUrl"
                    }
                    
                    // Rewrite localhost to match the host that actually resolved successfully via adb reverse.
                    val host = java.net.URL(endpoint).host
                    finalUrl = finalUrl.replace("localhost", host).replace("127.0.0.1", host)

                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Stream Resolved -> $finalUrl")
                    return finalUrl
                } else {
                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Cobalt success but no URL. Status: ${cobalt.status}, Text: ${cobalt.text}")
                }
            } else {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "❌ YouTubeExtractor: Cobalt returned non-2xx status: ${response.status.value}")
            }
        } catch (e: Exception) {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Exception during tryCobalt to $endpoint: ${e.message}")
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
            "http://localhost:9099/",
            "http://127.0.0.1:9099/",
            "http://10.0.2.2:9099/"
        )

        for (endpoint in localEndpoints) {
            val url = tryCobalt(endpoint, item.videoId)
            if (url != null) {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "🚀 YouTubeExtractor: Extraction successful for ${item.videoId}")
                return item.copy(localFilePath = url).right()
            }
        }

        com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "💀 YouTubeExtractor: All extraction attempts failed for ${item.videoId}")
        return "Extraction Failed: Cobalt is unreachable. Run 'adb reverse tcp:9099 tcp:9099'".left()
    }
}
