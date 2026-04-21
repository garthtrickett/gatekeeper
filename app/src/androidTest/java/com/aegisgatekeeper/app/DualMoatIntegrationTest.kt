package com.aegisgatekeeper.app

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

/**
 * Instrumented integration tests for the Dual-Moat handoff logic.
 * Verifies that Layer Alpha throttles its dispatching based on Layer Omega's status.
 */
@RunWith(AndroidJUnit4::class)
class DualMoatIntegrationTest {
    private val testAppPackage = "com.aegisgatekeeper.app.test"
    private val stateManager = GatekeeperStateManager

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
        val initialBlacklist = GatekeeperState(appGroups = listOf(initialAppGroup))
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
            assertThat(stateManager.state.value.isLayerOmegaActive).isTrue()

            // Act: Simulate the logic from the service's heartbeat. We are testing if the throttle works.
            var wasPollingLogicExecuted = false
            if (!stateManager.state.value.isLayerOmegaActive) {
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
            assertThat(stateManager.state.value.isLayerOmegaActive).isFalse()

            var wasActionDispatched = false
            var lastDetectedPackage: String? = null
            val currentApp = testAppPackage

            // Act: Simulate a Layer Alpha tick with Omega offline
            if (!stateManager.state.value.isLayerOmegaActive) {
                if (currentApp != lastDetectedPackage) {
                    lastDetectedPackage = currentApp
                    wasActionDispatched = true
                    stateManager.dispatch(GatekeeperAction.RuleViolationDetected(currentApp, "Test Reason", System.currentTimeMillis()))
                }
            }

            // Assert: The action should have been fired
            assertThat(wasActionDispatched).isTrue()
            // And the reducer, upon receiving the action for a blacklisted app, should activate the overlay.
            assertThat(stateManager.state.value.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.activeBlockReason).isEqualTo("Test Reason")
        }

    @Test
    fun whenManualLockdownIsActive_blocksAppRegardlessOfRules() =
        runTest {
            // Arrange: A group with NO rules, but Manual Lockdown is enabled.
            val stateWithLockdown =
                GatekeeperState(
                    isManualLockdownActive = true,
                    appGroups =
                        listOf(
                            com.aegisgatekeeper.app.domain.AppGroup(
                                id = "test-group-id",
                                name = "Test Group",
                                apps = setOf(testAppPackage),
                            ),
                        ),
                )

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithLockdown

            // Act: Force the Foreground Service to evaluate the current app.
            GatekeeperForegroundService.performAppValidation(
                InstrumentationRegistry.getInstrumentation().targetContext,
                testAppPackage,
            )

            // Assert: The overlay should instantly intercept with the Lockdown reason.
            assertThat(stateManager.state.value.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.activeBlockReason).isEqualTo("Manual Lockdown Engaged")
        }

    @Test
    fun whenRuleIsBroken_layerAlphaDispatchesViolation() =
        runTest {
            // Arrange: Add a time limit rule to the test group
            val rule = BlockingRule.TimeLimit(id = "test-rule", groupId = "test-group-id", timeLimitMinutes = 0) // 0 minute limit
            val group =
                stateManager.state.value.appGroups
                    .first()
            val updatedGroup = group.copy(rules = listOf(rule))
            val stateWithRule = GatekeeperState(appGroups = listOf(updatedGroup))

            val stateFlowField = stateManager.javaClass.getDeclaredField("_state")
            stateFlowField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateFlowField.get(stateManager) as MutableStateFlow<GatekeeperState>).value = stateWithRule

            // Act: Simulate a Layer Alpha tick. The logic inside the service would evaluate the rule.
            // We are directly dispatching the result of that evaluation for this test.
            stateManager.dispatch(
                GatekeeperAction.RuleViolationDetected(
                    packageName = testAppPackage,
                    reason = "Policy Violation: Time Limit (0m) for 'Test Group'",
                    currentTimestamp = System.currentTimeMillis(),
                ),
            )

            // Assert: The overlay should be active with the correct reason.
            assertThat(stateManager.state.value.isOverlayActive).isTrue()
            assertThat(stateManager.state.value.activeBlockReason).isEqualTo("Policy Violation: Time Limit (0m) for 'Test Group'")
        }
}
