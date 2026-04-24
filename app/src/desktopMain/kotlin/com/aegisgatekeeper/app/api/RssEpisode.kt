package com.aegisgatekeeper.app.api

data class RssEpisode(
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
)
