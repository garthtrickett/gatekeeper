package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject

@Serializable
data class ItunesSearchResponse(
    val results: List<ItunesPodcastDto> = emptyList(),
)

@Serializable
data class ItunesPodcastDto(
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artistName: String? = null,
)

@Inject
@Singleton
class PodcastIndexClient(private val client: HttpClient) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>> {
        if (query.isBlank()) return emptyList<PodcastFeedDto>().right()

        return try {
            val response =
                client.get("https://itunes.apple.com/search") {
                    header("User-Agent", "AegisGatekeeper/1.0")
                    parameter("media", "podcast")
                    parameter("entity", "podcast")
                    parameter("limit", 25)
                    parameter("term", query)
                }
            if (response.status.value in 200..299) {
                val responseText = response.bodyAsText()
                val itunesResponse = jsonParser.decodeFromString(ItunesSearchResponse.serializer(), responseText)
                val feeds =
                    itunesResponse.results.mapNotNull {
                        if (it.feedUrl != null && it.collectionName != null) {
                            PodcastFeedDto(
                                id = it.collectionId ?: 0L,
                                title = it.collectionName,
                                url = it.feedUrl,
                                image = it.artworkUrl600,
                                author = it.artistName,
                            )
                        } else {
                            null
                        }
                    }
                feeds.right()
            } else {
                "HTTP Error: ${response.status.value}".left()
            }
        } catch (e: Exception) {
            (e.message ?: "Unknown network failure").left()
        }
    }
}
