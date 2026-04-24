package com.aegisgatekeeper.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aegisgatekeeper.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

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

        val databaseProvider = StandaloneDatabaseProvider(this)
        val downloadDirectory = File(getExternalFilesDir(null), "downloads")
        downloadCache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)

        downloadManager = DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            androidx.media3.datasource.DefaultHttpDataSource.Factory(),
            Executors.newFixedThreadPool(6)
        )
    }
}
