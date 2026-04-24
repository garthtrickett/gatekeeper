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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class PodcastSearchResponse(
    @SerialName("feeds") val feeds: List<PodcastFeedDto> = emptyList()
)

object PodcastIndexClient {
    private const val API_KEY = "DUMMY_KEY"
    private const val API_SECRET = "DUMMY_SECRET"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>> {
        if (query.isBlank()) return emptyList<PodcastFeedDto>().right()

        return try {
            val unixTime = (System.currentTimeMillis() / 1000L).toString()
            val data4Hash = API_KEY + API_SECRET + unixTime
            val hash = MessageDigest.getInstance("SHA-1").digest(data4Hash.toByteArray())
            val authHeader = hash.joinToString("") { "%02x".format(it) }

            val response = client.get("https://api.podcastindex.org/api/1.0/search/byterm") {
                header("X-Auth-Key", API_KEY)
                header("X-Auth-Date", unixTime)
                header("Authorization", authHeader)
                header("User-Agent", "AegisGatekeeper/1.0")
                parameter("q", query)
            }
            if (response.status.value in 200..299) {
                response.body<PodcastSearchResponse>().feeds.right()
            } else {
                "HTTP Error: ${response.status.value}".left()
            }
        } catch (e: Exception) {
            (e.message ?: "Unknown network failure").left()
        }
    }
}
