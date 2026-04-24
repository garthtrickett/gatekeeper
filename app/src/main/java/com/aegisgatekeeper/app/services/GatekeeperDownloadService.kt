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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GatekeeperDownloadService :
    DownloadService(
        1007,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        "gatekeeper_downloads_channel",
        R.string.app_name,
        R.string.accessibility_description,
    ) {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var pollingJob: Job? = null

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
                            val pct = if (download.percentDownloaded == -1f) 0f else download.percentDownloaded
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.DownloadProgressUpdated(download.request.id, pct),
                            )
                            startPolling(downloadManager)
                        }

                        Download.STATE_COMPLETED -> {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.DownloadCompleted(download.request.id, "cached_in_simplecache"),
                            )
                            checkStopPolling(downloadManager)
                        }

                        Download.STATE_FAILED -> {
                            GatekeeperStateManager.dispatch(GatekeeperAction.DownloadFailed(download.request.id))
                            checkStopPolling(downloadManager)
                        }

                        else -> {
                            checkStopPolling(downloadManager)
                        }
                    }
                }
            },
        )
        return manager
    }

    private fun startPolling(manager: DownloadManager) {
        if (pollingJob?.isActive == true) return
        pollingJob =
            serviceScope.launch {
                while (isActive) {
                    var hasDownloading = false
                    manager.currentDownloads.forEach { download ->
                        if (download.state == Download.STATE_DOWNLOADING) {
                            hasDownloading = true
                            val pct = if (download.percentDownloaded == -1f) 0f else download.percentDownloaded
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.DownloadProgressUpdated(download.request.id, pct),
                            )
                        }
                    }
                    if (!hasDownloading) {
                        break
                    }
                    delay(500)
                }
            }
    }

    private fun checkStopPolling(manager: DownloadManager) {
        if (manager.currentDownloads.none { it.state == Download.STATE_DOWNLOADING }) {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
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
