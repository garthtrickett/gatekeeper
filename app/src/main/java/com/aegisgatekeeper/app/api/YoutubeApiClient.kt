package com.aegisgatekeeper.app.api

import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.BuildConfig
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
    private const val API_KEY = BuildConfig.YOUTUBE_API_KEY

    internal var client =
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

    /**
     * Fetches a maximum of 5 videos of medium or long duration.
     * This inherently filters out Shorts.
     * Returns a typed Either to adhere to Railway Oriented Programming principles.
     */
    suspend fun searchVideos(query: String): Either<YoutubeError, YoutubeSearchResponse> {
        // Prevent API calls for blank queries
        if (query.isBlank()) {
            return YoutubeSearchResponse(emptyList()).right()
        }

        return try {
            val response =
                client
                    .get("search") {
                        parameter("key", API_KEY)
                        parameter("part", "snippet")
                        parameter("q", query)
                        parameter("type", "video")
                        parameter("maxResults", 5)
                        parameter("videoDuration", "medium") // or "long"
                    }
            when (response.status.value) {
                in 200..299 -> response.body<YoutubeSearchResponse>().right()
                403, 429 -> YoutubeError.RateLimitExceeded.left()
                404 -> YoutubeError.VideoNotFound.left()
                else -> YoutubeError.UnknownError(response.status.value).left()
            }
        } catch (e: Exception) {
            Log.e("Gatekeeper", "YouTube API Call Failed: ${e.message}")
            YoutubeError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    /**
     * Fetches video details including ISO 8601 duration (e.g. PT15M33S)
     */
    suspend fun getVideoDetails(videoId: String): Either<YoutubeError, YoutubeVideoDetailsResponse> {
        Log.d("Gatekeeper", "📡 YoutubeApiClient: Fetching details for video '$videoId'")
        return try {
            val response =
                client
                    .get("videos") {
                        parameter("key", API_KEY)
                        parameter("part", "snippet,contentDetails")
                        parameter("id", videoId)
                    }
            when (response.status.value) {
                in 200..299 -> {
                    Log.d("Gatekeeper", "✅ YoutubeApiClient: Details fetch successful")
                    response.body<YoutubeVideoDetailsResponse>().right()
                }

                403, 429 -> {
                    Log.e("Gatekeeper", "❌ YoutubeApiClient: Rate limit exceeded")
                    YoutubeError.RateLimitExceeded.left()
                }

                404 -> {
                    Log.e("Gatekeeper", "❌ YoutubeApiClient: Video not found")
                    YoutubeError.VideoNotFound.left()
                }

                else -> {
                    Log.e("Gatekeeper", "❌ YoutubeApiClient: Details Call Failed: API returned ${response.status.value}")
                    YoutubeError.UnknownError(response.status.value).left()
                }
            }
        } catch (e: Exception) {
            Log.e("Gatekeeper", "❌ YoutubeApiClient: Details Call Failed: ${e.message}")
            YoutubeError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }
}
