package com.gatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun testIsVaultUnlocked_Before6PM_ReturnsFalse() {
        assertThat(isVaultUnlocked(LocalTime.of(17, 59))).isFalse()
        assertThat(isVaultUnlocked(LocalTime.of(10, 0))).isFalse()
    }

    @Test
    fun testIsVaultUnlocked_Between6PMAnd630PM_ReturnsTrue() {
        assertThat(isVaultUnlocked(LocalTime.of(18, 0))).isTrue()
        assertThat(isVaultUnlocked(LocalTime.of(18, 15))).isTrue()
        assertThat(isVaultUnlocked(LocalTime.of(18, 29))).isTrue()
    }

    @Test
    fun testIsVaultUnlocked_After630PM_ReturnsFalse() {
        assertThat(isVaultUnlocked(LocalTime.of(18, 30))).isFalse()
        assertThat(isVaultUnlocked(LocalTime.of(20, 0))).isFalse()
    }
}
