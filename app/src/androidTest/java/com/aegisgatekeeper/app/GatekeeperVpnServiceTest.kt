package com.aegisgatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.google.common.truth.Truth.assertThat
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

    @org.junit.After
    fun tearDown() {
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

            // 2. Manually construct state to isolate unit test from global Database side-effects
            val group = AppGroup(
                id = "group1",
                name = "Test",
                apps = setOf("com.example.app"),
                rules = listOf(
                    BlockingRule.DomainBlock(
                        id = "rule1",
                        groupId = "group1",
                        domains = setOf("youtube.com")
                    )
                )
            )
            val stateWithRule = GatekeeperState(
                appGroups = listOf(group),
                activeForegroundApp = "com.example.app"
            )

            // 4. Update the state in the service manually
            service.updateBlacklist(stateWithRule)

            // 5. Verify the internal blacklist is updated
            assertThat(getActiveBlacklist(service)).contains("youtube.com")

            // 6. Change foreground app to one not in the group
            val stateOtherApp = stateWithRule.copy(activeForegroundApp = "com.other.app")
            service.updateBlacklist(stateOtherApp)
            assertThat(getActiveBlacklist(service)).isEmpty()
        }

    @Test
    fun testVpnService_emptyAppGroup_isGloballyActive() =
        runBlocking<Unit> {
            val service = GatekeeperVpnService()
            val group = AppGroup(
                id = "group1",
                name = "Test Global",
                apps = emptySet(),
                rules = listOf(
                    BlockingRule.DomainBlock(
                        id = "rule1",
                        groupId = "group1",
                        domains = setOf("global.com")
                    )
                )
            )
            val state = GatekeeperState(
                appGroups = listOf(group),
                activeForegroundApp = "com.any.app"
            )
            service.updateBlacklist(state)
            assertThat(getActiveBlacklist(service)).contains("global.com")
        }
}
