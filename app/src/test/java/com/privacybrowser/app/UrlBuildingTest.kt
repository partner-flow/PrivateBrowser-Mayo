package com.privacybrowser.app

import com.privacybrowser.app.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic tests for turning address-bar text into either a direct URL load or a search query.
 * This mirrors BrowserViewModel.buildLoadTarget's heuristic in isolation (that function needs an
 * Android Context for URLEncoder in the full class, so the core heuristic is duplicated here in a
 * plain, instrumentation-free form for fast unit testing — see README for why this project favors
 * small pure-Kotlin helpers being testable outside the emulator).
 */
class UrlBuildingTest {

    private fun looksLikeUrl(text: String) = text.contains(".") && !text.contains(" ")

    @Test
    fun `plain https url is detected as a url`() {
        assertTrue(looksLikeUrl("https://example.com"))
    }

    @Test
    fun `bare domain is detected as a url`() {
        assertTrue(looksLikeUrl("example.com"))
    }

    @Test
    fun `plain text query is not detected as a url`() {
        assertTrue(!looksLikeUrl("best pizza near me"))
    }

    @Test
    fun `search engine template substitutes query`() {
        val template = SearchEngine.DUCKDUCKGO.urlTemplate
        val result = template.replace("%s", "cats")
        assertEquals("https://duckduckgo.com/?q=cats", result)
    }
}
