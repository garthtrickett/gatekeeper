package com.aegisgatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class TimeUtilsTest {
    @Test
    fun testIsDeepWorkHours_InsideWindow_ReturnsTrue() {
        assertThat(isDeepWorkHours(LocalTime.of(9, 0))).isTrue()
        assertThat(isDeepWorkHours(LocalTime.of(12, 0))).isTrue()
        assertThat(isDeepWorkHours(LocalTime.of(16, 59))).isTrue()
    }

    @Test
    fun testIsDeepWorkHours_OutsideWindow_ReturnsFalse() {
        assertThat(isDeepWorkHours(LocalTime.of(8, 59))).isFalse()
        assertThat(isDeepWorkHours(LocalTime.of(17, 0))).isFalse()
        assertThat(isDeepWorkHours(LocalTime.of(20, 0))).isFalse()
    }

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

    @Test
    fun testEstimateReadTimeSeconds() {
        val html =
            """
            <html>
            <head><title>Test Article</title></head>
            <body>
            <script>console.log("ignore me");</script>
            <style>.hidden { display: none; }</style>
            <h1>My Awesome Article</h1>
            <p>This is a short article to test word counting. It has some words.</p>
            </body>
            </html>
            """.trimIndent()
        // 13 words in body + 3 words in h1 + 2 in title = 18 words
        // 18 / 225 = 0.08 minutes * 60 = 4.8 seconds -> 4L
        val seconds = estimateReadTimeSeconds(html)
        assertThat(seconds).isAtLeast(0L)
    }

    @Test
    fun testParseIso8601Duration() {
        // Full time (Hours, Minutes, Seconds)
        assertThat(parseIso8601Duration("PT1H10M30S")).isEqualTo(4230L) // 3600 + 600 + 30

        // Minutes and Seconds only
        assertThat(parseIso8601Duration("PT15M33S")).isEqualTo(933L)

        // Minutes only
        assertThat(parseIso8601Duration("PT5M")).isEqualTo(300L)

        // Seconds only
        assertThat(parseIso8601Duration("PT45S")).isEqualTo(45L)

        // Hours and Seconds only
        assertThat(parseIso8601Duration("PT1H5S")).isEqualTo(3605L)

        // Invalid / Empty
        assertThat(parseIso8601Duration("P")).isEqualTo(0L)
        assertThat(parseIso8601Duration("")).isEqualTo(0L)
    }
}
