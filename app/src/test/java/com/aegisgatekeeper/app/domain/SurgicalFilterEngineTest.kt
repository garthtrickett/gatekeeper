package com.aegisgatekeeper.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SurgicalFilterEngineTest {
    private lateinit var engine: SurgicalFilterEngine

    @Before
    fun setup() {
        engine = SurgicalFilterEngine()
    }

    @Test
    fun `compiles network rules correctly`() {
        val rules =
            listOf(
                "||doubleclick.net^",
                "||ads.twitter.com^",
            )
        engine.compile(rules)

        assertThat(engine.shouldBlockRequest("https://doubleclick.net/js/script.js", "https://example.com")).isTrue()
        assertThat(engine.shouldBlockRequest("https://ads.twitter.com/tracker", "https://example.com")).isTrue()
        assertThat(engine.shouldBlockRequest("https://safe.com/js", "https://example.com")).isFalse()
    }

    @Test
    fun `compiles cosmetic rules correctly`() {
        val rules =
            listOf(
                "twitter.com,x.com##[data-testid='sidebarColumn']",
                "youtube.com###secondary",
            )
        engine.compile(rules)

        val twitterCss = engine.getCosmeticCss("https://twitter.com/home")
        assertThat(twitterCss).contains("[data-testid='sidebarColumn']")
        assertThat(twitterCss).doesNotContain("#secondary")

        val ytCss = engine.getCosmeticCss("https://youtube.com/watch?v=123")
        assertThat(ytCss).contains("#secondary")
        assertThat(ytCss).doesNotContain("[data-testid='sidebarColumn']")
    }

    @Test
    fun `ignores comments and empty lines`() {
        val rules =
            listOf(
                "! This is a comment",
                "",
                "  ",
                "||tracker.com^",
            )
        engine.compile(rules)

        assertThat(engine.shouldBlockRequest("https://tracker.com/pixel", "https://example.com")).isTrue()
    }

    @Test
    fun `generic cosmetic rules apply to all domains`() {
        val rules = listOf("##.generic-ad-banner")
        engine.compile(rules)

        val css1 = engine.getCosmeticCss("https://random-site.com")
        val css2 = engine.getCosmeticCss("https://another-site.com")

        assertThat(css1).contains(".generic-ad-banner")
        assertThat(css2).contains(".generic-ad-banner")
    }
}
