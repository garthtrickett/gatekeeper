package com.gatekeeper.app

import android.util.Log
import com.gatekeeper.app.db.DatabaseManager
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.domain.GatekeeperState
import com.gatekeeper.app.domain.SessionLog
import com.gatekeeper.app.domain.VaultItem
import com.gatekeeper.app.domain.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GatekeeperStateManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val db = DatabaseManager.db

    private val initialState: GatekeeperState by lazy {
        // --- Database Seeding (one-time on first launch) ---
        if (db.blacklistedAppQueries
                .selectAll()
                .executeAsList()
                .isEmpty()
        ) {
            Log.i("Gatekeeper", "DB: Seeding initial blacklist...")
            val initialBlacklist =
                setOf(
                    "com.android.chrome",
                    "org.mozilla.firefox",
                    "com.instagram.android",
                )
            db.transaction {
                initialBlacklist.forEach { packageName ->
                    db.blacklistedAppQueries.insert(packageName)
                }
            }
        }

        // --- Load initial state from database ---
        val blacklistedApps =
            db.blacklistedAppQueries
                .selectAll()
                .executeAsList()
                .toSet()
        val vaultItemsFromDb = db.vaultItemQueries.selectAll().executeAsList()
        val sessionLogsFromDb = db.sessionLogQueries.selectAll().executeAsList()

        GatekeeperState(
            blacklistedApps = blacklistedApps,
            vaultItems = vaultItemsFromDb.map { VaultItem(it.id, it.query, it.capturedAtTimestamp, it.isResolved) },
            sessionLogs = sessionLogsFromDb.map { SessionLog(it.id, it.packageName, it.durationMillis, it.emotion, it.loggedAtTimestamp) },
        )
    }

    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    fun dispatch(action: GatekeeperAction) {
        val actionName = action::class.simpleName ?: "UnknownAction"

        if (action !is GatekeeperAction.AppBroughtToForeground) {
            Log.d("Gatekeeper", "📥 Action Dispatched: $actionName")
        } else {
            if (_state.value.blacklistedApps.contains(action.packageName)) {
                Log.d("Gatekeeper", "📥 Action Dispatched: $actionName (${action.packageName})")
            }
        }

        val currentState = _state.value
        val newState = reduce(currentState, action)

        // Only update and trigger side-effects if the state has actually changed.
        if (newState != currentState) {
            _state.value = newState
            handleSideEffects(action, currentState, newState)
        }
    }

    private fun handleSideEffects(
        action: GatekeeperAction,
        oldState: GatekeeperState,
        newState: GatekeeperState,
    ) {
        scope.launch {
            when (action) {
                is GatekeeperAction.SaveToVault -> {
                    // The reducer appends the new item. The `minus` operator finds it.
                    val newItem = (newState.vaultItems - oldState.vaultItems.toSet()).firstOrNull()
                    newItem?.let {
                        Log.i("Gatekeeper", "DB: Inserting new VaultItem: ${it.id}")
                        db.vaultItemQueries.insert(
                            id = it.id,
                            query = it.query,
                            capturedAtTimestamp = it.capturedAtTimestamp,
                            isResolved = it.isResolved,
                        )
                    }
                }

                is GatekeeperAction.MarkVaultItemResolved -> {
                    Log.i("Gatekeeper", "DB: Marking VaultItem as resolved: ${action.id}")
                    db.vaultItemQueries.markAsResolved(id = action.id)
                }

                is GatekeeperAction.LogSessionMetacognition -> {
                    val newLog = (newState.sessionLogs - oldState.sessionLogs.toSet()).firstOrNull()
                    newLog?.let {
                        Log.i("Gatekeeper", "DB: Inserting new SessionLog: ${it.id}")
                        db.sessionLogQueries.insert(
                            id = it.id,
                            packageName = it.packageName,
                            durationMillis = it.durationMillis,
                            emotion = it.emotion,
                            loggedAtTimestamp = it.loggedAtTimestamp,
                        )
                    }
                }

                is GatekeeperAction.EmergencyBypassRequested -> {
                    Log.i("Gatekeeper", "DB: Logging Emergency Bypass for ${action.packageName}")
                    db.emergencyBypassLogQueries.insert(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        packageName = action.packageName,
                        reason = action.reason,
                        timestamp = action.currentTimestamp,
                    )
                }

                else -> { /* No database side effect for this action */ }
            }
        }
    }
}
