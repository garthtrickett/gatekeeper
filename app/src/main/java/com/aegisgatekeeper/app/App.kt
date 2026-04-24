package com.aegisgatekeeper.app

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aegisgatekeeper.app.sync.SyncWorker
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class App :
    Application(),
    Configuration.Provider {
    companion object {
        lateinit var instance: App
            private set

        lateinit var downloadCache: SimpleCache
            private set
        lateinit var downloadManager: DownloadManager
            private set
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Schedule periodic podcast refresh (every 6 hours)
        val podcastConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val podcastRefreshRequest = PeriodicWorkRequestBuilder<com.aegisgatekeeper.app.sync.PodcastRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(podcastConstraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic-podcast-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            podcastRefreshRequest
        )

        val databaseProvider = StandaloneDatabaseProvider(this)
        val downloadDirectory = File(getExternalFilesDir(null), "downloads")
        downloadCache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)

        downloadManager =
            DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                androidx.media3.datasource.DefaultHttpDataSource
                    .Factory(),
                Executors.newFixedThreadPool(6),
            )
    }
}
