package com.aegisgatekeeper.app.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects (DTOs) for the YouTube Data API v3 search endpoint.
 * We only model the fields we need to reduce parsing overhead.
 */

sealed interface YoutubeError {
    object RateLimitExceeded : YoutubeError

    object VideoNotFound : YoutubeError

    data class NetworkFailure(
        val message: String,
    ) : YoutubeError

    data class UnknownError(
        val code: Int,
    ) : YoutubeError
}

@Serializable
data class YoutubeSearchResponse(
    @SerialName("items") val items: List<YoutubeSearchItem>,
)

@Serializable
data class YoutubeSearchItem(
    @SerialName("id") val id: ItemId,
    @SerialName("snippet") val snippet: YoutubeSnippet,
)

@Serializable
data class ItemId(
    @SerialName("videoId") val videoId: String,
)

@Serializable
data class YoutubeSnippet(
    @SerialName("title") val title: String,
    @SerialName("channelTitle") val channelTitle: String,
    @SerialName("thumbnails") val thumbnails: Thumbnails,
)

@Serializable
data class Thumbnails(
    @SerialName("high") val high: ThumbnailInfo,
)

@Serializable
data class ThumbnailInfo(
    @SerialName("url") val url: String,
)

@Serializable
data class YoutubeVideoDetailsResponse(
    @SerialName("items") val items: List<VideoDetailItem> = emptyList(),
)

@Serializable
data class VideoDetailItem(
    @SerialName("snippet") val snippet: VideoSnippet? = null,
    @SerialName("contentDetails") val contentDetails: ContentDetails? = null,
)

@Serializable
data class VideoSnippet(
    @SerialName("title") val title: String? = null,
    @SerialName("channelTitle") val channelTitle: String? = null,
)

@Serializable
data class ContentDetails(
    @SerialName("duration") val duration: String? = null,
)
