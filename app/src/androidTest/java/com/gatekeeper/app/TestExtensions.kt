package com.gatekeeper.app

import com.gatekeeper.app.domain.GatekeeperState
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
        val stateFlowField = this.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        (stateFlowField.get(this) as MutableStateFlow<GatekeeperState>).value = GatekeeperState()
    } catch (e: Exception) {
        // Fallback for an unlikely but possible issue with Proguard/R8 in release test builds.
        // This re-initializes the entire object, which is heavier but effective.
        val instanceField = this.javaClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, this.javaClass.getConstructor().newInstance())
    }
}
