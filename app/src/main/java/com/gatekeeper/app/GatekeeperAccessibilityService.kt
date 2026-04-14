package com.gatekeeper.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.gatekeeper.app.domain.GatekeeperAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class GatekeeperAccessibilityService : AccessibilityService() {
    private val stateManager = GatekeeperStateManager
    private lateinit var serviceScope: CoroutineScope

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        Log.i("Gatekeeper", "✅ Accessibility Service Connected")

        startForegroundService(Intent(this, GatekeeperForegroundService::class.java))
        Log.i("Gatekeeper", "⚓ Anti-Kill Foreground Service Launched")

        observeState()
    }

    private fun observeState() {
        serviceScope.launch {
            stateManager.state
                .distinctUntilChangedBy { it.isOverlayActive }
                .collect { state ->
                    if (state.isOverlayActive) {
                        Log.i("Gatekeeper", "STATE-DRIVEN: Showing overlay for ${state.currentlyInterceptedApp}")
                        // CRITICAL: Must use 'this' (the AccessibilityService context)
                        GatekeeperOverlay.show(this@GatekeeperAccessibilityService)
                    } else {
                        Log.i("Gatekeeper", "STATE-DRIVEN: Removing overlay")
                        GatekeeperOverlay.remove(this@GatekeeperAccessibilityService)
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        stateManager.dispatch(GatekeeperAction.AppBroughtToForeground(packageName, System.currentTimeMillis()))
    }

    override fun onInterrupt() {
        Log.w("Gatekeeper", "Accessibility Service Interrupted")
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
