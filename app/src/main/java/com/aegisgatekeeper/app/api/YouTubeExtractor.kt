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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private suspend fun fetchDynamicInvidiousInstances(): List<String> {
        return try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Fetching dynamic instance list from api.invidious.io...")
            val response = client.get("https://api.invidious.io/instances.json?sort_by=health") {
                timeout {
                    requestTimeoutMillis = 5000
                    connectTimeoutMillis = 5000
                }
            }
            if (response.status.value in 200..299) {
                val jsonArray = parser.decodeFromString(JsonArray.serializer(), response.bodyAsText())
                val instances = jsonArray.mapNotNull { element ->
                    val tuple = element.jsonArray
                    if (tuple.size >= 2) {
                        val details = tuple[1].jsonObject
                        val isApi = details["api"]?.jsonPrimitive?.booleanOrNull == true
                        val uri = details["uri"]?.jsonPrimitive?.contentOrNull
                        if (isApi && uri != null) uri else null
                    } else null
                }
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Found ${instances.size} healthy dynamic instances.")
                instances
            } else {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Dynamic fetch failed with status ${response.status.value}")
                emptyList()
            }
        } catch (e: Exception) {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Dynamic fetch failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun tryPiped(instance: String, videoId: String): String? {
        try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Trying Piped ($instance)")
            val response = client.get("$instance/streams/$videoId") {
                timeout {
                    requestTimeoutMillis = 5000
                    connectTimeoutMillis = 5000
                }
            }

            if (response.status.value in 200..299) {
                val text = response.bodyAsText()
                val pipedResponse = parser.decodeFromString(PipedResponse.serializer(), text)
                
                if (pipedResponse.error != null && pipedResponse.audioStreams.isNullOrEmpty()) return null

                val stream = pipedResponse.audioStreams?.firstOrNull { it.format == "M4A" || it.mimeType?.contains("audio/mp4") == true }
                    ?: pipedResponse.audioStreams?.firstOrNull()

                if (stream != null) {
                    com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ YouTubeExtractor: Found Piped stream")
                    return stream.url
                }
            } else {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Piped instance failed with status ${response.status.value}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Piped instance failed: ${e.message}")
        }
        return null
    }

    private suspend fun tryInvidious(instance: String, videoId: String): String? {
        try {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "📡 YouTubeExtractor: Trying Invidious ($instance)")
            val response = client.get("$instance/api/v1/videos/$videoId") {
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
                    return stream.url
                }
            } else {
                com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Invidious instance failed with status ${response.status.value}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "⚠️ YouTubeExtractor: Invidious instance failed: ${e.message}")
        }
        return null
    }

    suspend fun extractVideo(item: ContentItem): Either<String, ContentItem> {
        if (com.aegisgatekeeper.app.App.isRunningTest) {
            val placeholderStreamUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
            return item.copy(localFilePath = placeholderStreamUrl).right()
        }

        // 1. Try Dynamic Invidious Instances First
        val dynamicInvidious = fetchDynamicInvidiousInstances()
        for (instance in dynamicInvidious) {
            val url = tryInvidious(instance, item.videoId)
            if (url != null) return item.copy(localFilePath = url).right()
        }

        // 2. Try Fallback Piped Instances
        val fallbackPiped = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.smnz.de",
            "https://pipedapi.adminforge.de",
            "https://piped-api.lunar.icu"
        )
        for (instance in fallbackPiped) {
            val url = tryPiped(instance, item.videoId)
            if (url != null) return item.copy(localFilePath = url).right()
        }

        // 3. Try Fallback Invidious Instances (if dynamic failed)
        val fallbackInvidious = listOf(
            "https://invidious.lunar.icu",
            "https://inv.tux.pizza",
            "https://invidious.projectsegfau.lt",
            "https://yewtu.be"
        )
        for (instance in fallbackInvidious) {
            if (dynamicInvidious.contains(instance)) continue // Skip if already tried in step 1
            val url = tryInvidious(instance, item.videoId)
            if (url != null) return item.copy(localFilePath = url).right()
        }

        return "Could not extract YouTube stream from any available instance".left()
    }
}
