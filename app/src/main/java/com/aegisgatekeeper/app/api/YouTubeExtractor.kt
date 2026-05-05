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
    val downloadMode: String = "audio",
    val audioFormat: String = "mp3"
)

@Serializable
data class CobaltError(
    val code: String? = null
)

@Serializable
data class CobaltResponse(
    val status: String? = null,
    val url: String? = null,
    val error: CobaltError? = null
)

@Inject
@Singleton
class YouTubeExtractor(private val client: HttpClient) {
    private val parser = Json { ignoreUnknownKeys = true }

    private suspend fun tryCobalt(endpoint: String, videoId: String): String? {
        try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Trying Local Cobalt ($endpoint)")
            val response = client.post(endpoint) {
                contentType(io.ktor.http.ContentType.Application.Json)
                header("Accept", "application/json")
                setBody(CobaltRequest(url = "https://www.youtube.com/watch?v=$videoId"))
                timeout {
                    requestTimeoutMillis = 8000
                    connectTimeoutMillis = 5000
                }
            }

            val text = response.bodyAsText()
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "🔍 Cobalt Response: $text")
            
            if (response.status.value in 200..299) {
                val cobaltResponse = parser.decodeFromString(CobaltResponse.serializer(), text)
                
                if (cobaltResponse.url != null && (cobaltResponse.status == "tunnel" || cobaltResponse.status == "redirect" || cobaltResponse.status == "stream")) {
                    var finalUrl = cobaltResponse.url
                    if (finalUrl.startsWith("/")) {
                        finalUrl = "${endpoint.trimEnd('/')}$finalUrl"
                    }
                    
                    // REWRITE LOGIC: Ensure the emulator always points to the host machine (10.0.2.2)
                    // if Cobalt returns a loopback address.
                    val isEmulator = endpoint.contains("10.0.2.2")
                    if (isEmulator) {
                        finalUrl = finalUrl.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
                    }
                    
                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Found stream -> $finalUrl")
                    return finalUrl
                }
            }
        } catch (e: Exception) {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Exception connecting to $endpoint - ${e.message}")
        }
        return null
    }

    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            val placeholderStreamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
            return item.copy(localFilePath = placeholderStreamUrl).right()
        }

        // We try the emulator-specific host loopback FIRST
        val localEndpoints = listOf(
            "http://10.0.2.2:9099/",
            "http://localhost:9099/",
            "http://127.0.0.1:9099/"
        )

        for (endpoint in localEndpoints) {
            val url = tryCobalt(endpoint, item.videoId)
            if (url != null) {
                return item.copy(localFilePath = url).right()
            }
        }

        return "Extraction Failed: Local Cobalt container is unreachable or returned an error.".left()
    }
}
