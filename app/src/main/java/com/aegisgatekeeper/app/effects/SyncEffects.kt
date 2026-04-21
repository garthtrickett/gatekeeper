package com.aegisgatekeeper.app.effects

import android.util.Log
import com.aegisgatekeeper.app.auth.SecureTokenStorage
import com.aegisgatekeeper.app.db.GatekeeperDatabase
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState

fun handleSyncAndAuthEffects(
    action: GatekeeperAction,
    newState: GatekeeperState,
    db: GatekeeperDatabase,
) {
    when (action) {
        is GatekeeperAction.LoginSuccess -> {
            SecureTokenStorage.saveToken(action.token)
        }

        GatekeeperAction.Logout -> {
            SecureTokenStorage.clearToken()
        }

        is GatekeeperAction.RequestMagicLink -> {
            Log.i("Gatekeeper", "API: Requesting magic link for ${action.email}")
            // Actual API call to backend would go here
        }

        is GatekeeperAction.RemoteSyncCompleted -> {
            Log.i("Gatekeeper", "DB: Upserting remotely synced items.")
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
                        videoId = it.videoId,
                        title = it.title,
                        source = it.source,
                        type = it.type,
                        rank = it.rank,
                        capturedAtTimestamp = it.capturedAtTimestamp,
                        durationSeconds = it.durationSeconds,
                        lastModified = it.lastModified,
                        isSynced = true, // Mark as synced
                        isDeleted = it.isDeleted,
                    )
                }
            }
        }

        else -> { /* Other actions don't interact with sync/auth in this handler */ }
    }
}
