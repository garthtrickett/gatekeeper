package com.aegisgatekeeper.app.views

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure JVM unit test for the Gauntlet level generation logic.
 */
class BallBalancingUiLogicTest {
    @Test
    fun `generateGauntletLevel increases difficulty with bypass count`() {
        // Tier 1: 0 bypasses
        val level1 = generateGauntletLevel(0)
        assertThat(level1.barriers.size).isEqualTo(4)
        assertThat(level1.barriers.first().gapWidth).isEqualTo(0.25f)

        // Tier 2: 2 bypasses
        val level2 = generateGauntletLevel(2)
        assertThat(level2.barriers.size).isEqualTo(6)
        assertThat(level2.barriers.first().gapWidth).isWithin(0.001f).of(0.21f)

        // Max difficulty reached
        val levelMax = generateGauntletLevel(100)
        assertThat(levelMax.barriers.size).isEqualTo(8)
        assertThat(levelMax.barriers.first().gapWidth).isWithin(0.001f).of(0.15f)
    }
}
