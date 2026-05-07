package com.aegisgatekeeper.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.VaultItem
import com.aegisgatekeeper.app.sync.ContentItemDto
import com.aegisgatekeeper.app.sync.SyncClient
import com.aegisgatekeeper.app.sync.SyncPushPayload
import com.aegisgatekeeper.app.sync.VaultItemDto

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        Log.i("Gatekeeper", "⚙️ SyncWorker: Starting background sync.")

        val state = GatekeeperStateManager.state.value
        if (!state.sync.isAuthenticated) {
            Log.i("Gatekeeper", "⚙️ SyncWorker: Not authenticated. Skipping sync.")
            return Result.success()
        }

        return try {
            val db = DatabaseManager.db

            // 1. Push Local Changes
            val vaultDtos =
                db.vaultItemQueries.selectAll().executeAsList().map {
                    VaultItemDto(it.id, it.query, it.capturedAtTimestamp, it.isResolved, it.lastModified, it.isDeleted)
                }

            val contentDtos =
                db.contentItemQueries.selectAllByRank().executeAsList().map {
                    ContentItemDto(
                        id = it.id,
                        podcastId = it.podcastId,
                        videoId = it.videoId,
                        title = it.title,
                        channelName = it.channelName,
                        source = it.source.name,
                        type = it.type.name,
                        rank = it.rank,
                        capturedAtTimestamp = it.capturedAtTimestamp,
                        durationSeconds = it.durationSeconds,
                        lastModified = it.lastModified,
                        isDeleted = it.isDeleted,
                    )
                }

            val pushResult =
                com.aegisgatekeeper.app.di.GlobalDI.component.syncClient
                    .pushChanges(SyncPushPayload(vaultDtos, contentDtos))
            val pushFailed =
                pushResult.fold(
                    ifLeft = { error ->
                        Log.w("Gatekeeper", "❌ SyncWorker: Push failed: $error")
                        true
                    },
                    ifRight = { false },
                )
            if (pushFailed) return Result.retry()

            val filterResult =
                com.aegisgatekeeper.app.di.GlobalDI.component.syncClient
                    .fetchFilterRules()
            filterResult.fold(
                ifLeft = { error ->
                    Log.w("Gatekeeper", "❌ SyncWorker: Filter rules pull failed: $error")
                },
                                                ifRight = { result ->
                    if (state.media.filterRulesHash != result.hash) {
                        GatekeeperStateManager.dispatch(GatekeeperAction.UpdateFilterRules(result.rules, result.hash))
                        Log.i("Gatekeeper", "✅ SyncWorker: Downloaded ${result.rules.size} filter rules (New Hash).")
                    } else {
                        Log.i("Gatekeeper", "✅ SyncWorker: Filter rules unchanged.")
                    }
                },
            )

            // 2. Pull Remote Changes
            val pullResult =
                com.aegisgatekeeper.app.di.GlobalDI.component.syncClient
                    .pullChanges(0L)
            return pullResult.fold(
                ifLeft = { error ->
                    Log.w("Gatekeeper", "❌ SyncWorker: Pull failed: $error")
                    Result.retry()
                },
                ifRight = { payload ->
                    val newVaultItems =
                        payload.vaultItems.map {
                            VaultItem(it.id, it.query, it.capturedAtTimestamp, it.isResolved, it.lastModified, true, it.isDeleted)
                        }
                    // Temporary simplification for PodcastSubscriptions until fully managed in RemoteSyncCompleted

                    val newContentItems =
                        payload.contentItems.map {
                            ContentItem(
                                id = it.id,
                                podcastId = it.podcastId,
                                videoId = it.videoId,
                                title = it.title,
                                channelName = it.channelName,
                                source = ContentSource.valueOf(it.source),
                                type = ContentType.valueOf(it.type),
                                rank = it.rank,
                                capturedAtTimestamp = it.capturedAtTimestamp,
                                durationSeconds = it.durationSeconds,
                                lastModified = it.lastModified,
                                isSynced = true,
                                isDeleted = it.isDeleted,
                            )
                        }

                    GatekeeperStateManager.dispatch(GatekeeperAction.RemoteSyncCompleted(newVaultItems, newContentItems))
                    Log.i("Gatekeeper", "✅ SyncWorker: Background sync completed successfully.")
                    Result.success()
                },
            )
        } catch (e: Exception) {
            Log.w("Gatekeeper", "❌ SyncWorker: Background sync failed: ${e.message}")
            Result.retry()
        }
    }
}
