package com.aegisgatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field

@RunWith(AndroidJUnit4::class)
class GatekeeperVpnServiceTest {
    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
    }

    private fun getActiveBlacklist(service: GatekeeperVpnService): Set<String> {
        val field: Field = GatekeeperVpnService::class.java.getDeclaredField("activeBlacklist")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(service) as Set<String>
    }

    @Test
    fun testVpnService_updatesBlacklist_whenStateChanges() =
        runBlocking<Unit> {
            val service = GatekeeperVpnService()

            // 1. Initial state should have an empty blacklist
            assertThat(getActiveBlacklist(service)).isEmpty()

            // 2. Add a group and a domain block rule
            GatekeeperStateManager.dispatch(GatekeeperAction.CreateAppGroup("group1", "Test", setOf("com.example.app")))
            GatekeeperStateManager.dispatch(GatekeeperAction.AddDomainBlockRule("rule1", "group1", setOf("youtube.com")))

            // 3. Set the foreground app to trigger the blacklist update
            GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground("com.example.app", System.currentTimeMillis()))

            // 4. Update the state in the service manually
            service.updateBlacklist(GatekeeperStateManager.state.value)

            // 5. Verify the internal blacklist is updated
            assertThat(getActiveBlacklist(service)).contains("youtube.com")

            // 6. Change foreground app to one not in the group
            GatekeeperStateManager.dispatch(GatekeeperAction.AppBroughtToForeground("com.other.app", System.currentTimeMillis()))
            service.updateBlacklist(GatekeeperStateManager.state.value)
            assertThat(getActiveBlacklist(service)).isEmpty()
        }
}
