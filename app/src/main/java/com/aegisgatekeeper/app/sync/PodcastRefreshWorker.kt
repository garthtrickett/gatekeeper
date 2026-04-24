package com.aegisgatekeeper.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.api.RssClient
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import java.util.UUID

class PodcastRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.i("Gatekeeper", "⚙️ PodcastRefreshWorker: Starting background RSS refresh.")
        val db = DatabaseManager.db
        val subscriptions = db.podcastSubscriptionQueries.selectAll().executeAsList()

        if (subscriptions.isEmpty()) {
            Log.i("Gatekeeper", "⚙️ PodcastRefreshWorker: No subscriptions found.")
            GatekeeperStateManager.dispatch(GatekeeperAction.PodcastSyncCompleted)
            return Result.success()
        }

        var anyFailures = false

        for (sub in subscriptions) {
            val result = RssClient.fetchFeed(sub.feedUrl)
            result.fold(
                ifLeft = { error ->
                    Log.w("Gatekeeper", "❌ PodcastRefreshWorker: Failed to fetch feed ${sub.feedUrl}: $error")
                    anyFailures = true
                },
                ifRight = { data ->
                    Log.i("Gatekeeper", "✅ PodcastRefreshWorker: Fetched ${data.episodes.size} episodes for ${sub.showTitle}")
                    db.transaction {
                        data.episodes.forEach { ep ->
                            val id = UUID.nameUUIDFromBytes(ep.audioUrl.toByteArray()).toString()
                            db.podcastEpisodeQueries.insertOrReplace(
                                id = id,
                                podcastId = sub.id,
                                title = ep.title,
                                audioUrl = ep.audioUrl,
                                durationSeconds = ep.durationSeconds,
                                pubDate = ep.pubDate,
                                lastModified = System.currentTimeMillis()
                            )
                        }
                        db.podcastEpisodeQueries.deleteOldEpisodes(sub.id, 200)
                    }

                    // If this podcast is currently being viewed, refresh the UI
                    if (GatekeeperStateManager.state.value.activePodcastId == sub.id) {
                        GatekeeperStateManager.dispatch(GatekeeperAction.LoadPodcastEpisodes(sub.feedUrl, sub.id))
                    }
                }
            )
        }

        GatekeeperStateManager.dispatch(GatekeeperAction.PodcastSyncCompleted)

        return if (anyFailures) Result.retry() else Result.success()
    }
}
