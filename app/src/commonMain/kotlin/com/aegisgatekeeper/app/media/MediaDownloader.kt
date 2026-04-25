package com.aegisgatekeeper.app.media

interface MediaDownloader {
    fun enqueueDownload(
        id: String,
        url: String,
    )

    fun removeDownload(id: String)
}
