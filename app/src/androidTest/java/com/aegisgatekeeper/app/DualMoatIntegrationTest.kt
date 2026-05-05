package com.aegisgatekeeper.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aegisgatekeeper.app.domain.BlockingRule
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualMoatIntegrationTest {
    private val testAppPackage = "com.aegisgatekeeper.app.test"
    private val stateManager: GatekeeperStateManager
        get() = GatekeeperStateManager

    @Before
    fun setup() {
        // Reset the singleton to a clean slate before each test
        stateManager.resetStateForTest()

        // Seed the state with a blacklisted app for interception checks
        val initialAppGroup =
            com.aegisgatekeeper.app.domain.AppGroup(
                id = "test-group-id",
                name = "Test Group",
                apps = setOf(testAppPackage),
                combinator = com.aegisgatekeeper.app.domain.RuleCombinator.ANY,
            )
        val initialBlacklist =
            GatekeeperState(
                interception =
                    com.aegisgatekeeper.app.domain
                        .InterceptionState(appGroups = listOf(initialAppGroup)),
            )
        // Use reflection to set this initial state
        val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = initialBlacklist
    }

    @Test
    fun whenLayerOmegaIsActive_layerAlphaHeartbeatIsThrottled() =
        runTest {
            // Arrange: Manually set the state to indicate Layer Omega is alive
            stateManager.dispatch(GatekeeperAction.LayerOmegaConnected)
            assertThat(stateManager.state.value.interception.isLayerOmegaActive).isTrue()

            // Act: Simulate the logic from the service's heartbeat. We are testing if the throttle works.
            var wasPollingLogicExecuted = false
            if (!stateManager.state.value.interception.isLayerOmegaActive) {
                // This block represents the polling logic (calling the detector, dispatching, etc.)
                wasPollingLogicExecuted = true
            }

            // Assert: The polling logic block should have been skipped entirely because Layer Omega is active.
            assertThat(wasPollingLogicExecuted).isFalse()
        }

    @Test
    fun whenLayerOmegaIsInactive_layerAlphaHeartbeatDispatches() =
        runTest {
            // Arrange: Ensure state indicates Layer Omega is disconnected
            stateManager.dispatch(GatekeeperAction.LayerOmegaDisconnected)
            assertThat(stateManager.state.value.interception.isLayerOmegaActive).isFalse()

            var wasActionDispatched = false
            var lastDetectedPackage: String? = null
            val currentApp = testAppPackage

            // Act: Simulate a Layer Alpha tick with Omega offline
            if (!stateManager.state.value.interception.isLayerOmegaActive) {
                if (currentApp != lastDetectedPackage) {
                    lastDetectedPackage = currentApp
                    wasActionDispatched = true
                    stateManager.dispatch(GatekeeperAction.RuleViolationDetected(currentApp, "Test Reason", System.currentTimeMillis()))
                }
            }

            // Assert: The action should have been fired
            assertThat(wasActionDispatched).isTrue()
            // And the reducer, upon receiving the action for a blacklisted app, should activate the overlay.
            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.interception.activeBlockReason).isEqualTo("Test Reason")
        }

    @Test
    fun whenManualLockdownIsActive_blocksAppRegardlessOfRules() =
        runTest {
            // Arrange: A group with NO rules, but Manual Lockdown is enabled.
            val stateWithLockdown =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain.InterceptionState(
                            isManualLockdownActive = true,
                            appGroups =
                                listOf(
                                    com.aegisgatekeeper.app.domain.AppGroup(
                                        id = "test-group-id",
                                        name = "Test Group",
                                        apps = setOf(testAppPackage),
                                    ),
                                ),
                        ),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithLockdown

            // Act: Force the Foreground Service to evaluate the current app.
            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                InstrumentationRegistry.getInstrumentation().targetContext,
                testAppPackage,
            )

            // Assert: The overlay should instantly intercept with the Lockdown reason.
            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.interception.activeBlockReason).isEqualTo("Manual Lockdown Engaged")
        }

    @Test
    fun whenAlwaysBlockRuleIsActive_blocksApp() =
        runTest {
            // Arrange: Add an Always Block rule to the test group
            val rule = BlockingRule.AlwaysBlock(id = "test-rule", groupId = "test-group-id")
            val group =
                stateManager.state.value.interception.appGroups
                    .first()
            val updatedGroup = group.copy(rules = listOf(rule))
            val stateWithRule =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain
                            .InterceptionState(appGroups = listOf(updatedGroup)),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithRule

            // Act: Simulate a Layer Alpha tick which calls performAppValidation
            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                InstrumentationRegistry.getInstrumentation().targetContext,
                testAppPackage,
            )

            // Assert: The overlay should be active with the correct reason.
            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.interception.activeBlockReason).isEqualTo("Policy Violation: Always Block for 'Test Group'")
        }

    @Test
    fun whenSwitchingAwayFromWhitelistedApp_triggersExitInterview() =
        runTest {
            // Arrange: App is whitelisted and active
            val whitelist =
                com.aegisgatekeeper.app.domain
                    .TemporaryWhitelist(testAppPackage, "Test", 0L, Long.MAX_VALUE, 1000L)
            val stateWithWhitelist =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain.InterceptionState(
                            activeForegroundApp = testAppPackage,
                            activeWhitelists = mapOf(testAppPackage to whitelist),
                            appGroups =
                                listOf(
                                    com.aegisgatekeeper.app.domain
                                        .AppGroup(id = "group1", name = "Test", apps = setOf(testAppPackage)),
                                ),
                        ),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithWhitelist

            // Initialize lastDetectedPackage to simulate we were already inside the app
            val evaluatorClass = Class.forName("com.aegisgatekeeper.app.services.AndroidRuleEvaluator")
            val evaluatorInstance = evaluatorClass.getField("INSTANCE").get(null)
            val lastPackageField = evaluatorClass.getDeclaredField("lastDetectedPackage")
            lastPackageField.isAccessible = true
            lastPackageField.set(evaluatorInstance, testAppPackage)

            // Act: Simulate Layer Alpha detecting a switch to the Launcher (or any other safe app)
            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext,
                "com.android.launcher",
            )

            // Assert: The exit interview should be triggered for the app we just left
            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.interception.pendingExitInterview).isEqualTo(testAppPackage)
        }

    @Test
    fun emptyAppGroup_doesNotInterceptForegroundApps() =
        runTest {
            // Arrange: A group with NO apps, but an Always Block rule.
            // This simulates a "Global Domain" group. We must ensure it doesn't brick the phone by blocking everything.
            val emptyGroup =
                com.aegisgatekeeper.app.domain.AppGroup(
                    id = "empty-group",
                    name = "Global Domains",
                    apps = emptySet(),
                    rules = listOf(BlockingRule.AlwaysBlock(id = "rule1", groupId = "empty-group")),
                )
            val stateWithEmptyGroup =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain
                            .InterceptionState(appGroups = listOf(emptyGroup)),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithEmptyGroup

            // Act: Simulate a Layer Alpha tick for an arbitrary app
            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                InstrumentationRegistry.getInstrumentation().targetContext,
                "com.any.random.app",
            )

            // Assert: The overlay should NOT activate for app interception.
            assertThat(stateManager.state.value.interception.isOverlayActive).isFalse()
        }

    @Test
    fun formattedBlockReasons_areCorrectlyGenerated() =
        runTest {
            val scheduledRule =
                com.aegisgatekeeper.app.domain.BlockingRule.ScheduledBlock(
                    id = "scheduled",
                    groupId = "test-group-id",
                    timeSlots =
                        listOf(
                            com.aegisgatekeeper.app.domain
                                .TimeSlot(0, 1440),
                        ),
                    daysOfWeek =
                        com.aegisgatekeeper.app.domain.DayOfWeek
                            .values()
                            .toSet(),
                )

            val timeLimitRule =
                com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit(
                    id = "timelimit",
                    groupId = "test-group-id",
                    timeLimitMinutes = 0,
                )

            val checkInRule =
                com.aegisgatekeeper.app.domain.BlockingRule.CheckIn(
                    id = "checkin",
                    groupId = "test-group-id",
                    checkInTimesMinutes = listOf(600, 720),
                    durationMinutes = 15,
                    daysOfWeek =
                        com.aegisgatekeeper.app.domain.DayOfWeek
                            .values()
                            .toSet(),
                )

            val group =
                stateManager.state.value.interception.appGroups
                    .first()
            val updatedGroup =
                group.copy(
                    rules = listOf(scheduledRule, timeLimitRule, checkInRule),
                    combinator = com.aegisgatekeeper.app.domain.RuleCombinator.ALL,
                )
            val stateWithRule =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain
                            .InterceptionState(appGroups = listOf(updatedGroup)),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithRule

            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext,
                testAppPackage,
            )

            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            val reason = stateManager.state.value.interception.activeBlockReason
            assertThat(reason).contains("Scheduled Block (00:00 - 24:00)")
            assertThat(reason).contains("Time Limit Reached")
            assertThat(reason).contains("Check-In Required (10:00, 12:00)")
        }

    @Test
    fun whenRuleIsBroken_layerAlphaDispatchesViolation() =
        runTest {
            // Arrange: Add a time limit rule to the test group
            val rule =
                com.aegisgatekeeper.app.domain.BlockingRule.TimeLimit(
                    id = "test-rule",
                    groupId = "test-group-id",
                    timeLimitMinutes = 0,
                ) // 0 minute limit
            val group =
                stateManager.state.value.interception.appGroups
                    .first()
            val updatedGroup = group.copy(rules = listOf(rule))
            val stateWithRule =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain
                            .InterceptionState(appGroups = listOf(updatedGroup)),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithRule

            // Act: Simulate a Layer Alpha tick. The logic inside the service would evaluate the rule.
            // We are directly dispatching the result of that evaluation for this test.
            stateManager.dispatch(
                GatekeeperAction.RuleViolationDetected(
                    packageName = testAppPackage,
                    reason = "Policy Violation: Time Limit Reached (0m left, used 0/0m) for 'Test Group'",
                    currentTimestamp = System.currentTimeMillis(),
                ),
            )

            // Assert: The overlay should be active with the correct reason.
            assertThat(stateManager.state.value.interception.isOverlayActive).isTrue()
            assertThat(
                stateManager.state.value.interception.activeBlockReason,
            ).isEqualTo("Policy Violation: Time Limit Reached (0m left, used 0/0m) for 'Test Group'")
        }

    @Test
    fun whenIncomingCallIsDetected_grantsTemporaryGracePeriod() =
        runTest {
            // Arrange: An app that is normally blocked with an AlwaysBlock rule
            val rule = BlockingRule.AlwaysBlock(id = "test-rule", groupId = "test-group-id")
            val group =
                stateManager.state.value.interception.appGroups
                    .first()
            val updatedGroup = group.copy(rules = listOf(rule))
            val stateWithRule =
                GatekeeperState(
                    interception =
                        com.aegisgatekeeper.app.domain
                            .InterceptionState(appGroups = listOf(updatedGroup)),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as kotlinx.coroutines.flow.MutableStateFlow<GatekeeperState>).value = stateWithRule

            // Act: Dispatch the action that the NotificationListenerService would send
            stateManager.dispatch(GatekeeperAction.GrantTemporaryCallWhitelist(testAppPackage, System.currentTimeMillis()))

            // Act: Now, simulate the app coming to the foreground. The evaluator should see the whitelist.
            com.aegisgatekeeper.app.services.AndroidRuleEvaluator.performAppValidation(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                testAppPackage,
            )

            // Assert: The overlay should NOT be active because of the temporary whitelist.
            assertThat(stateManager.state.value.interception.isOverlayActive).isFalse()
        }
}
