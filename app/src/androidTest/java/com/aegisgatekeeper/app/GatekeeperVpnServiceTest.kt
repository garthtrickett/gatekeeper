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
            val group =
                AppGroup(
                    id = "group1",
                    name = "Test",
                    apps = setOf("com.example.app"),
                    rules =
                        listOf(
                            BlockingRule.DomainBlock(
                                id = "rule1",
                                groupId = "group1",
                                domains = setOf("youtube.com"),
                            ),
                        ),
                )
                        val stateWithRule =
                GatekeeperState(
                    interception = com.aegisgatekeeper.app.domain.InterceptionState(
                        appGroups = listOf(group),
                        activeForegroundApp = "com.example.app",
                    )
                )

            // 4. Update the state in the service manually
            service.updateBlacklist(stateWithRule)

            // 5. Verify the internal blacklist is updated
            assertThat(getActiveBlacklist(service)).contains("youtube.com")

            // 6. Change foreground app to one not in the group
            // Since domain blocks are now globally applied, the blacklist should STILL contain the domain.
                        val stateOtherApp = stateWithRule.copy(interception = stateWithRule.interception.copy(activeForegroundApp = "com.other.app"))
            service.updateBlacklist(stateOtherApp)
            assertThat(getActiveBlacklist(service)).contains("youtube.com")
        }

    @Test
    fun testVpnService_emptyAppGroup_isGloballyActive() =
        runBlocking<Unit> {
            val service = GatekeeperVpnService()
            val group =
                AppGroup(
                    id = "group1",
                    name = "Test Global",
                    apps = emptySet(),
                    rules =
                        listOf(
                            BlockingRule.DomainBlock(
                                id = "rule1",
                                groupId = "group1",
                                domains = setOf("global.com"),
                            ),
                        ),
                )
                        val state =
                GatekeeperState(
                    interception = com.aegisgatekeeper.app.domain.InterceptionState(
                        appGroups = listOf(group),
                        activeForegroundApp = "com.any.app",
                    )
                )
            service.updateBlacklist(state)
            assertThat(getActiveBlacklist(service)).contains("global.com")
        }

    @Test
    fun testVpnService_isDomainBlocked_matchesCorrectly() =
        runBlocking<Unit> {
            val service = GatekeeperVpnService()

            val group =
                AppGroup(
                    id = "group1",
                    name = "Test",
                    apps = setOf("com.example.app"),
                    rules =
                        listOf(
                            BlockingRule.DomainBlock(id = "rule1", groupId = "group1", domains = setOf("reddit.com")),
                        ),
                )
                        val state = GatekeeperState(interception = com.aegisgatekeeper.app.domain.InterceptionState(appGroups = listOf(group), activeForegroundApp = "com.example.app"))
            service.updateBlacklist(state)

            val method = GatekeeperVpnService::class.java.getDeclaredMethod("isDomainBlocked", String::class.java)
            method.isAccessible = true

            // Assert correct exact and subdomain blocking behavior
            assertThat(method.invoke(service, "reddit.com")).isEqualTo(true)
            assertThat(method.invoke(service, "www.reddit.com")).isEqualTo(true)
            assertThat(method.invoke(service, "np.reddit.com")).isEqualTo(true)

            // Assert it does NOT over-block (which the old .endsWith logic would have done)
            assertThat(method.invoke(service, "myreddit.com")).isEqualTo(false)
            assertThat(method.invoke(service, "reddit.com.org")).isEqualTo(false)
        }

    @Test
    fun testVpnService_extractDomainName_handlesMalformedPacketsSafely() {
        val service = GatekeeperVpnService()
        val method =
            GatekeeperVpnService::class.java.getDeclaredMethod(
                "extractDomainName",
                ByteArray::class.java,
                Int::class.java,
                Int::class.java,
            )
        method.isAccessible = true

        // DNS Header = 12 bytes. So offset = 0 means domain starts at index 12.
        val payload = ByteArray(15)
        payload[12] = 3 // Length of first part is 3
        payload[13] = 'a'.code.toByte()
        payload[14] = 'b'.code.toByte()
        // End of array, no null terminator, should not crash due to our new limit checks

        val result = method.invoke(service, payload, 0, 15) as String
        assertThat(result).isEqualTo("ab") // Extracted what it could safely before hitting limit
    }
}
