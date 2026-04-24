package com.aegisgatekeeper.app.media

expect object MediaDownloader {
    fun enqueueDownload(id: String, url: String)
    fun removeDownload(id: String)
}
