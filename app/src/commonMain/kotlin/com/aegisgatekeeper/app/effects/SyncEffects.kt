package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.platformLog

fun handleSyncAndAuthEffects(
    action: GatekeeperAction,
    newState: GatekeeperState,
    db: GatekeeperDatabase,
) {
    when (action) {
        is GatekeeperAction.LoginSuccess -> {
            // Secure token storage is still platform specific but handled differently later, ignoring for pure port right now
            platformLog("Gatekeeper", "✅ LoginSuccess: Token received")
        }

        GatekeeperAction.Logout -> {
            platformLog("Gatekeeper", "🚪 Logout: Clearing tokens")
        }

        is GatekeeperAction.RequestMagicLink -> {
            platformLog("Gatekeeper", "🌐 API: Requesting magic link for ${action.email}")
        }

        is GatekeeperAction.RemoteSyncCompleted -> {
            platformLog("Gatekeeper", "🗄️ DB: Upserting remotely synced items.")
            db.transaction {
                newState.vaultItems.forEach {
                    db.vaultItemQueries.insert(
                        id = it.id,
                        query = it.query,
                        capturedAtTimestamp = it.capturedAtTimestamp,
                        isResolved = it.isResolved,
                        lastModified = it.lastModified,
                        isSynced = true, // Mark as synced
                        isDeleted = it.isDeleted,
                    )
                }
                newState.contentItems.forEach {
                    db.contentItemQueries.insert(
                        id = it.id,
                        podcastId = it.podcastId,
                        videoId = it.videoId,
                        title = it.title,
                        channelName = it.channelName,
                        source = it.source,
                        type = it.type,
                        rank = it.rank,
                        capturedAtTimestamp = it.capturedAtTimestamp,
                        durationSeconds = it.durationSeconds,
                        lastModified = it.lastModified,
                        isSynced = true, // Mark as synced
                        isDeleted = it.isDeleted,
                        localFilePath = it.localFilePath,
                        downloadStatus = it.downloadStatus,
                    )
                }
            }
        }

        else -> { /* Other actions don't interact with sync/auth in this handler */ }
    }
}