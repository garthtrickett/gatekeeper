package com.aegisgatekeeper.app.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleAggregatorTest {
    @Test
    fun `parseFilterRules drops unsupported selectors and merges correctly`() {
        val list1 = """
            ! Title: List 1
            ||example.com^
            youtube.com##.ad-banner
            youtube.com##.shorts:has(.title)
        """.trimIndent()

        val list2 = """
            ! Title: List 2
            ||tracker.com^
            youtube.com##.sponsored:xpath(//div)
            reddit.com##.promoted
            ||example.com^
        """.trimIndent()

        val result = SyncClient.parseFilterRules(listOf(list1, list2))

        assertThat(result.rules).containsExactly(
            "||example.com^",
            "||tracker.com^",
            "reddit.com##.promoted",
            "youtube.com##.ad-banner"
        ).inOrder()

        // The hash should be a non-empty string
        assertThat(result.hash).isNotEmpty()
    }
}