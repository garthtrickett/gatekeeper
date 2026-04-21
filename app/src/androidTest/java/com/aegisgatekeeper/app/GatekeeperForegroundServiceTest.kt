package com.aegisgatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the GatekeeperForegroundService lifecycle.
 */
@RunWith(AndroidJUnit4::class)
class GatekeeperForegroundServiceTest {
    @Test
    fun testServiceLifecycle_CancelsCoroutineScopeOnDestroy_PreventsMemoryLeak() {
        // Arrange
        // We instantiate the service directly to test its internal lifecycle hooks
        // without needing a complex bound ServiceTestRule setup.
        val service = GatekeeperForegroundService()

        // Use reflection to access the private serviceScope
        val scopeField = GatekeeperForegroundService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        val scope = scopeField.get(service) as CoroutineScope

        // Verify the scope starts out active
        assertThat(scope.isActive).isTrue()

        // Act: Simulate the OS destroying the service
        service.onDestroy()

        // Assert: The heartbeat scope MUST be cancelled to prevent infinite background polling loops
        assertThat(scope.isActive).isFalse()
    }
}
