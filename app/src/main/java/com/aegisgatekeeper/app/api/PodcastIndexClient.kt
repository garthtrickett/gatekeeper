package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ItunesSearchResponse(
    val results: List<ItunesPodcastDto> = emptyList()
)

@Serializable
data class ItunesPodcastDto(
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artistName: String? = null
)

object PodcastIndexClient {
    internal var client =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>> {
        if (query.isBlank()) return emptyList<PodcastFeedDto>().right()

        return try {
            val response =
                client.get("https://itunes.apple.com/search") {
                    parameter("media", "podcast")
                    parameter("term", query)
                }
            if (response.status.value in 200..299) {
                val responseText = response.bodyAsText()
                val parser = Json { ignoreUnknownKeys = true }
                val itunesResponse = parser.decodeFromString(ItunesSearchResponse.serializer(), responseText)
                val feeds = itunesResponse.results.mapNotNull {
                    if (it.feedUrl != null && it.collectionName != null) {
                        PodcastFeedDto(
                            id = it.collectionId ?: 0L,
                            title = it.collectionName,
                            url = it.feedUrl,
                            image = it.artworkUrl600,
                            author = it.artistName
                        )
                    } else null
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
