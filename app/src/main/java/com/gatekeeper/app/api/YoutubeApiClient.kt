package com.gatekeeper.app.api

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object YoutubeApiClient {
    // IMPORTANT: You must replace this with your actual YouTube Data API key.
    private const val API_KEY = "REPLACE_WITH_YOUR_YOUTUBE_API_KEY"

    private val client by lazy {
        HttpClient(OkHttp) {
            defaultRequest {
                url("https://www.googleapis.com/youtube/v3/")
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    },
                )
            }
        }
    }

    /**
     * Fetches a maximum of 5 videos of medium or long duration.
     * This inherently filters out Shorts.
     * Returns a Result type to adhere to Railway Oriented Programming principles.
     */
    suspend fun searchVideos(query: String): Result<YoutubeSearchResponse> {
        // Prevent API calls for blank queries
        if (query.isBlank()) {
            return Result.success(YoutubeSearchResponse(emptyList()))
        }

        return try {
            val response: YoutubeSearchResponse =
                client
                    .get("search") {
                        parameter("key", API_KEY)
                        parameter("part", "snippet")
                        parameter("q", query)
                        parameter("type", "video")
                        parameter("maxResults", 5)
                        parameter("videoDuration", "medium") // or "long"
                    }.body()
            Result.success(response)
        } catch (e: Exception) {
            Log.e("Gatekeeper", "YouTube API Call Failed: ${e.message}")
            Result.failure(e)
        }
    }
}
