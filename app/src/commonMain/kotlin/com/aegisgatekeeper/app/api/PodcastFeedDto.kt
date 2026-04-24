package com.aegisgatekeeper.app.api

import kotlinx.serialization.Serializable

@Serializable
data class PodcastFeedDto(
    val id: Long,
    val title: String,
    val url: String,
    val image: String? = null,
    val author: String? = null,
)
