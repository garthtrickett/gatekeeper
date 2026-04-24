package com.aegisgatekeeper.app.services

import android.app.Notification
import androidx.media3.common.util.NotificationUtil
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.R
import com.aegisgatekeeper.app.domain.GatekeeperAction

class GatekeeperDownloadService :
    DownloadService(
        1007,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        "gatekeeper_downloads_channel",
        R.string.app_name,
        R.string.accessibility_description,
    ) {
    override fun getDownloadManager(): DownloadManager {
        val manager = App.downloadManager
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    when (download.state) {
                        Download.STATE_DOWNLOADING -> {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.DownloadProgressUpdated(download.request.id, download.percentDownloaded),
                            )
                        }

                        Download.STATE_COMPLETED -> {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.DownloadCompleted(download.request.id, "cached_in_simplecache"),
                            )
                        }

                        Download.STATE_FAILED -> {
                            GatekeeperStateManager.dispatch(GatekeeperAction.DownloadFailed(download.request.id))
                        }
                    }
                }
            },
        )
        return manager
    }

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification =
        androidx.media3.exoplayer.offline
            .DownloadNotificationHelper(
                this,
                "gatekeeper_downloads_channel",
            ).buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                null,
                downloads,
                notMetRequirements,
            )
}
