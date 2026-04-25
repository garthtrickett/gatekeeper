package com.aegisgatekeeper.app.media

import android.net.Uri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.services.GatekeeperDownloadService

actual object MediaDownloader {
    actual fun enqueueDownload(
        id: String,
        url: String,
    ) {
        val request = DownloadRequest.Builder(id, Uri.parse(url)).build()
        DownloadService.sendAddDownload(
            App.instance,
            GatekeeperDownloadService::class.java,
            request,
            false,
        )
    }

    override fun removeDownload(id: String) {
        DownloadService.sendRemoveDownload(
            App.instance,
            GatekeeperDownloadService::class.java,
            id,
            false,
        )
    }
}
