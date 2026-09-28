package com.privacybrowser.app.model

import java.util.UUID

/** A single open browser tab. Not persisted across app restarts by design (see README). */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val isPrivate: Boolean,
    var title: String = "New Tab",
    var url: String = "",
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var faviconLoaded: Boolean = false,
    var loadError: LoadError? = null
)

data class LoadError(val description: String, val failingUrl: String?)

/** One entry in normal (non-private) browsing history. Never written to for private tabs. */
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val visitedAtEpochMs: Long
)

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createdAtEpochMs: Long
)

enum class SearchEngine(val label: String, val urlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s")
}

/**
 * Search engines above are offered as legitimate, real options. None of them is described here
 * as making browsing "anonymous" — see PrivacyInfoScreen for accurate, non-exaggerated language
 * about what each part of the app does and does not protect against.
 */
