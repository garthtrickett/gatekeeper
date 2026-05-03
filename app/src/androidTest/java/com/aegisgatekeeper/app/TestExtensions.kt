package com.aegisgatekeeper.app

import com.aegisgatekeeper.app.domain.GatekeeperState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A test-only utility to reset the singleton StateManager to its initial empty state.
 * This is crucial for preventing state from one test leaking into another and causing
 * unpredictable failures.
 */
fun GatekeeperStateManager.resetStateForTest() {
    // We use reflection to access the private state flow and reset it.
    // This avoids needing to make state mutable in the production code.
    try {
        // Cancel any pending background work (e.g. API calls from ProcessSharedLink)
        // to ensure side-effects don't bleed into the next test.
        val scopeField = this.javaClass.getDeclaredField("scope")
        scopeField.isAccessible = true
        val scope = scopeField.get(this) as CoroutineScope
        scope.coroutineContext.cancelChildren()

        val stateFlowField = this.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateFlowField.get(this) as MutableStateFlow<GatekeeperState>).value = GatekeeperState()

        try {
            val evaluatorClass = Class.forName("com.aegisgatekeeper.app.services.AndroidRuleEvaluator")
            val evaluatorInstance = evaluatorClass.getField("INSTANCE").get(null)
            val lastPackageField = evaluatorClass.getDeclaredField("lastDetectedPackage")
            lastPackageField.isAccessible = true
            lastPackageField.set(evaluatorInstance, null)
        } catch (e: Exception) {}
    } catch (e: Exception) {
        throw IllegalStateException("Failed to reset GatekeeperStateManager via reflection", e)
    }
}
