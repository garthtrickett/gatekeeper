package com.aegisgatekeeper.app

import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.di.GlobalDI
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.platformLog
import com.aegisgatekeeper.app.domain.reduce
import com.aegisgatekeeper.app.effects.handleDatabaseEffects
import com.aegisgatekeeper.app.effects.handleIntegrationEffects
import com.aegisgatekeeper.app.effects.handleMediaAndSystemEffects
import com.aegisgatekeeper.app.effects.handleSyncAndAuthEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object GatekeeperStateManager {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sideEffectDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(sideEffectDispatcher + SupervisorJob())

    private val db get() = DatabaseManager.db

    private val _state = MutableStateFlow(GatekeeperState())
    val state = _state.asStateFlow()

    fun dispatch(action: GatekeeperAction) {
        val actionName = action::class.simpleName ?: "UnknownAction"

                if (action !is GatekeeperAction.AppBroughtToForeground) {
            platformLog("Gatekeeper", "\ud83d\udce5 Action Dispatched: $actionName")
        } else {
            if (_state.value.interception.appGroups.any { it.apps.contains(action.packageName) }) {
                platformLog("Gatekeeper", "\ud83d\udce5 Action Dispatched: $actionName (${action.packageName})")
            }
        }

        val currentState = _state.value
        val newState = reduce(currentState, action)

        if (newState != currentState) {
            _state.value = newState
        }

        handleSideEffects(action, currentState, newState)
    }

    private fun handleSideEffects(
        action: GatekeeperAction,
        oldState: GatekeeperState,
        newState: GatekeeperState,
    ) {
        scope.launch {
            val effectHandler = GlobalDI.component.effectHandler
            handleDatabaseEffects(action, oldState, newState, db, ::dispatch)
            handleSyncAndAuthEffects(action, newState, db)
            handleMediaAndSystemEffects(action, oldState, newState, ::dispatch, effectHandler)
            handleIntegrationEffects(action, newState, ::dispatch, effectHandler)
        }
    }
}
