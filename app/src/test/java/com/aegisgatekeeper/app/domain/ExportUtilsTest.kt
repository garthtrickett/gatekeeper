package com.aegisgatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportUtilsTest {
    @Test
    fun testGenerateMarkdownReport() {
        // Arrange
        val vaultItems =
            listOf(
                VaultItem(query = "Mechanical keyboards", capturedAtTimestamp = 1000L, isResolved = true),
                VaultItem(query = "Herman Miller desk", capturedAtTimestamp = 2000L, isResolved = false),
            )
        val sessionLogs =
            listOf(
                SessionLog(packageName = "com.test.app", durationMillis = 60000L, emotion = Emotion.HAPPY, loggedAtTimestamp = 3000L),
            )

        // Act
        val markdown =
            generateMarkdownReport(
                vaultItems = vaultItems,
                sessionLogs = sessionLogs,
                bypassCount = 5,
                giveUpCount = 10,
            )

        // Assert
        assertThat(markdown).contains("- Total Bypasses: 5")
        assertThat(markdown).contains("- Total Give-Ups: 10")
        assertThat(markdown).contains("- [x] Mechanical keyboards (Captured: 1000)")
        assertThat(markdown).contains("- [ ] Herman Miller desk (Captured: 2000)")
        assertThat(markdown).contains("- com.test.app: 60s, Emotion: HAPPY")
    }
}
