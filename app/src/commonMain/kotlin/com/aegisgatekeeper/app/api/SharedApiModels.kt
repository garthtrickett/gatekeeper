package com.aegisgatekeeper.app.api

data class RssEpisode(
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
)

data class RssFeedData(
    val title: String,
    val artworkUrl: String?,
    val episodes: List<RssEpisode>,
)

data class ContentMetadata(
    val title: String,
    val durationSeconds: Long? = null,
    val resolvedUrl: String? = null,
)