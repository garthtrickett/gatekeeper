package com.aegisgatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit tests for the Dual-Moat state transitions in the reducer.
 */
class DualMoatReducerTest {
    private val initialState = GatekeeperState()

    @Test
    fun `reduce LayerOmegaConnected sets state to true`() {
        // Act
        val newState = reduce(initialState, GatekeeperAction.LayerOmegaConnected).state

        // Assert
        assertThat(newState.interception.isLayerOmegaActive).isTrue()
    }

    @Test
    fun `reduce LayerOmegaDisconnected sets state to false`() {
        // Arrange
        val connectedState = initialState.copy(interception = initialState.interception.copy(isLayerOmegaActive = true))

        // Act
        val newState = reduce(connectedState, GatekeeperAction.LayerOmegaDisconnected).state

        // Assert
        assertThat(newState.interception.isLayerOmegaActive).isFalse()
    }
}
