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

class App :
    Application(),
    Configuration.Provider {
    companion object {
        lateinit var instance: App
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
    }
}
