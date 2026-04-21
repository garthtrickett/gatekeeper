package com.aegisgatekeeper.app.services

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test for the deduplication logic used in Layer Alpha's heartbeat loop.
 * Ensures we don't spam the StateManager with duplicate events if the user
 * stays in the same app for multiple polling ticks.
 */
class LayerAlphaDeduplicationTest {
    @Test
    fun testHeartbeatDeduplication_OnlyDispatchesOnStateChange() {
        // Arrange
        var dispatchCount = 0
        var lastDetectedPackage: String? = null

        // Simulate the exact logic found in GatekeeperForegroundService.startLayerAlphaHeartbeat()
        fun simulateHeartbeatTick(currentApp: String?) {
            if (currentApp != null && currentApp != lastDetectedPackage) {
                lastDetectedPackage = currentApp
                dispatchCount++
            }
        }

        // Act & Assert

        simulateHeartbeatTick(null) // Empty state, should not dispatch
        assertThat(dispatchCount).isEqualTo(0)

        simulateHeartbeatTick("com.instagram.android") // New app detected
        assertThat(dispatchCount).isEqualTo(1)

        simulateHeartbeatTick("com.instagram.android") // Same app, should deduplicate
        assertThat(dispatchCount).isEqualTo(1)

        simulateHeartbeatTick("com.android.chrome") // Switched app, should dispatch
        assertThat(dispatchCount).isEqualTo(2)

        simulateHeartbeatTick("com.android.chrome") // Same app, should deduplicate
        assertThat(dispatchCount).isEqualTo(2)
    }
}
