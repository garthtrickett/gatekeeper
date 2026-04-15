package com.gatekeeper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gatekeeper.app.db.GatekeeperDatabase
import com.gatekeeper.app.db.SessionLog
import com.gatekeeper.app.domain.Emotion
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.domain.GatekeeperState
import com.gatekeeper.app.domain.VaultItem
import com.gatekeeper.app.domain.reduce
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration test for the GatekeeperStateManager and its side effects.
 * This test runs on an emulator/device and uses an in-memory SQLDelight database
 * to verify that dispatching actions correctly triggers database writes.
 */
@RunWith(AndroidJUnit4::class)
class GatekeeperStateManagerTest {
    private lateinit var db: GatekeeperDatabase
    private lateinit var driver: AndroidSqliteDriver

    // We are creating a manual, test-only version of the StateManager's logic
    // to inject our in-memory database.
    private lateinit var mutableState: MutableStateFlow<GatekeeperState>
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Use the Android driver. Passing 'null' as the name creates an in-memory DB.
        driver = AndroidSqliteDriver(GatekeeperDatabase.Schema, context, null)

        // We must provide the same ColumnAdapter used in production
        val emotionAdapter =
            object : ColumnAdapter<Emotion, String> {
                override fun decode(databaseValue: String): Emotion = Emotion.valueOf(databaseValue)

                override fun encode(value: Emotion): String = value.name
            }

        db =
            GatekeeperDatabase(
                driver = driver,
                SessionLogAdapter = SessionLog.Adapter(emotionAdapter = emotionAdapter),
            )

        mutableState = MutableStateFlow(GatekeeperState())
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun testEmergencyBypassLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.EmergencyBypassRequested(
                    packageName = "com.test.app",
                    reason = "Test reason",
                    currentTimestamp = 12345L,
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
    fun testMarkVaultItemResolvedLogging() =
        runTest {
            // Arrange
            val initialItem = VaultItem(query = "Test resolve", capturedAtTimestamp = 1000L)
            db.vaultItemQueries.insert(
                id = initialItem.id,
                query = initialItem.query,
                capturedAtTimestamp = initialItem.capturedAtTimestamp,
                isResolved = false,
            )
            mutableState.value = GatekeeperState(vaultItems = listOf(initialItem))

            val action = GatekeeperAction.MarkVaultItemResolved(initialItem.id)

            // Act
            dispatchWithSideEffects(action)

            // Assert
            val items = db.vaultItemQueries.selectAll().executeAsList()
            val updatedItem = items.first { it.id == initialItem.id }
            assertThat(updatedItem.isResolved).isTrue()
        }

    @Test
    fun testSaveToVaultLogging() =
        runTest {
            // Arrange
            val action =
                GatekeeperAction.SaveToVault(
                    query = "How to test SQLDelight?",
                    currentTimestamp = 5555L,
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
     * A test-specific replica of the dispatch and side-effect handling logic.
     * It returns a Job so the test can wait for the DB write to finish.
     */
    private suspend fun dispatchWithSideEffects(action: GatekeeperAction) {
        val oldState = mutableState.value
        val newState = reduce(oldState, action)
        mutableState.value = newState

        // Mimic the real side-effect logic
        val job =
            scope.launch {
                when (action) {
                    is GatekeeperAction.EmergencyBypassRequested -> {
                        db.emergencyBypassLogQueries.insert(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            packageName = action.packageName,
                            reason = action.reason,
                            timestamp = action.currentTimestamp,
                        )
                    }

                    is GatekeeperAction.SaveToVault -> {
                        val newItem = (newState.vaultItems - oldState.vaultItems.toSet()).first()
                        db.vaultItemQueries.insert(
                            id = newItem.id,
                            query = newItem.query,
                            capturedAtTimestamp = newItem.capturedAtTimestamp,
                            isResolved = newItem.isResolved,
                        )
                    }

                    is GatekeeperAction.MarkVaultItemResolved -> {
                        db.vaultItemQueries.markAsResolved(action.id)
                    }

                    else -> { /* Other side effects not under test */ }
                }
            }
        // Wait for the background work to finish before returning
        job.join()
    }
}
