package com.aegisgatekeeper.app.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying that Layer Alpha fails safely when system permissions are missing.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundAppDetectorTest {
    @Test
    fun testGetForegroundApp_WithoutUsageStatsPermission_ReturnsNullAndDoesNotCrash() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // This test is only valid if we DON'T have the permission. If the permission
        // is granted on the test device (as it is on some emulators), we should
        // skip the test rather than have it fail.
        assumeFalse(
            "Skipping test: USAGE_STATS permission is granted in this test environment.",
            PermissionChecker.hasUsageAccessPermission(context),
        )

        // Act
        // If our logic is flawed, this will throw a SecurityException or NullPointerException.
        val result = ForegroundAppDetector.getForegroundApp(context)

        // Assert
        // The detector should safely catch the missing permission and return null,
        // allowing the service to keep ticking without crashing the app.
        assertThat(result).isNull()
    }
}
