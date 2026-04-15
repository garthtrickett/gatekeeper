package com.gatekeeper.app.domain

import java.time.LocalTime

/**
 * Pure function to determine if the Vault is currently unlocked.
 * Hardcoded Check Window: 18:00 (6:00 PM) to 18:30 (6:30 PM).
 */
fun isVaultUnlocked(currentTime: LocalTime): Boolean {
    val start = LocalTime.of(18, 0)
    val end = LocalTime.of(18, 30)
    return !currentTime.isBefore(start) && currentTime.isBefore(end)
}
