package com.gatekeeper.app.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects (DTOs) for the YouTube Data API v3 search endpoint.
 * We only model the fields we need to reduce parsing overhead.
 */

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
