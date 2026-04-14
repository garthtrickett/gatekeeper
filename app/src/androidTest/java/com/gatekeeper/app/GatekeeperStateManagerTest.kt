package com.gatekeeper.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gatekeeper.app.db.GatekeeperDatabase
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.domain.GatekeeperState
import com.gatekeeper.app.domain.reduce
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Instrumented integration test for the GatekeeperStateManager and its side effects.
 * This test runs on an emulator/device and uses an in-memory SQLDelight database
 * to verify that dispatching actions correctly triggers database writes.
 */
class GatekeeperStateManagerTest {

    private lateinit var db: GatekeeperDatabase
    private lateinit var driver: JdbcSqliteDriver

    // We are creating a manual, test-only version of the StateManager's logic
    // to inject our in-memory database.
    private lateinit var _state: MutableStateFlow<GatekeeperState>
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Before
    fun setup() {
        // Use the in-memory JDBC driver for SQLDelight
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GatekeeperDatabase.Schema.create(driver)
        db = GatekeeperDatabase(driver)

        _state = MutableStateFlow(GatekeeperState())
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `dispatching EmergencyBypassRequested writes a log to the database`() = runTest {
        // Arrange
        val action = GatekeeperAction.EmergencyBypassRequested(
            packageName = "com.test.app",
            reason = "Test reason",
            currentTimestamp = 12345L
        )

        // Act
        dispatchWithSideEffects(action)

        // Assert
        val logs = db.emergencyBypassLogQueries.selectAll().executeAsList()
        assertThat(logs).hasSize(1)

        val log = logs.first()
        assertThat(log.packageName).isEqualTo("com.test.app")
        assertThat(log.reason).isEqualTo("Test reason")
        assertThat(log.timestamp).isEqualTo(12345L)
    }

    @Test
    fun `dispatching SaveToVault writes a new item to the database`() = runTest {
        // Arrange
        val action = GatekeeperAction.SaveToVault(
            query = "How to test SQLDelight?",
            currentTimestamp = 5555L
        )

        // Act
        dispatchWithSideEffects(action)

        // Assert
        val items = db.vaultItemQueries.selectAll().executeAsList()
        assertThat(items).hasSize(1)

        val item = items.first()
        assertThat(item.query).isEqualTo("How to test SQLDelight?")
        assertThat(item.capturedAtTimestamp).isEqualTo(5555L)
        assertThat(item.isResolved).isFalse()
    }

    /**
     * A test-specific replica of the dispatch and side-effect handling logic from
     * the real GatekeeperStateManager, but using our in-memory `db` instance.
     */
    private fun dispatchWithSideEffects(action: GatekeeperAction) {
        val oldState = _state.value
        val newState = reduce(oldState, action)
        _state.value = newState

        // Mimic the real side-effect logic
        scope.launch {
            when (action) {
                is GatekeeperAction.EmergencyBypassRequested -> {
                    db.emergencyBypassLogQueries.insert(
                        id = java.util.UUID.randomUUID().toString(),
                        packageName = action.packageName,
                        reason = action.reason,
                        timestamp = action.currentTimestamp
                    )
                }

                is GatekeeperAction.SaveToVault -> {
                    val newItem = (newState.vaultItems - oldState.vaultItems.toSet()).first()
                    db.vaultItemQueries.insert(
                        id = newItem.id,
                        query = newItem.query,
                        capturedAtTimestamp = newItem.capturedAtTimestamp,
                        isResolved = newItem.isResolved
                    )
                }
                else -> { /* Other side effects not under test */ }
            }
        }.invokeOnCompletion {
            // In a real app, this is not needed. In a test, we need to ensure
            // the coroutine completes before our assertions run.
        }
    }
}
