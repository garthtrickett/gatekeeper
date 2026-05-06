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
    val audioFormat: String = "best"
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
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Connecting to Cobalt ($endpoint)")
            val response = client.post(endpoint) {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("Accept", "application/json")
                if (com.aegisgatekeeper.app.BuildConfig.COBALT_API_KEY.isNotEmpty()) {
                    header("Api-Key", com.aegisgatekeeper.app.BuildConfig.COBALT_API_KEY)
                }
                setBody(CobaltRequest(url = requestUrl))
                timeout {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 5000
                }
            }

            if (response.status.value in 200..299) {
                val responseText = response.bodyAsText()
                val cobalt = parser.decodeFromString(CobaltResponse.serializer(), responseText)
                
                if (cobalt.url != null) {
                    var finalUrl = cobalt.url!!
                    if (finalUrl.startsWith("/")) {
                        val base = endpoint.substringBefore("/api")
                        finalUrl = "${base.trimEnd('/')}$finalUrl"
                    }
                    
                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Stream Resolved")
                    return finalUrl
                }
            }
        } catch (e: Exception) {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Attempt failed for $endpoint: ${e.message}")
        }
        return null
    }

        suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            return item.copy(localFilePath = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4").right()
        }

        val url = tryCobalt(com.aegisgatekeeper.app.BuildConfig.COBALT_API_URL, item.videoId)
        if (url != null) {
            return item.copy(localFilePath = url).right()
        }

        return "Extraction Failed: Cobalt unreachable. Ensure your API key is correct in local.properties.".left()
    }
}
