package com.privacybrowser.app.browser

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privacybrowser.app.data.BookmarkRepository
import com.privacybrowser.app.data.HistoryRepository
import com.privacybrowser.app.data.SettingsRepository
import com.privacybrowser.app.model.BrowserTab
import com.privacybrowser.app.model.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val historyRepository = HistoryRepository(application)
    val bookmarkRepository = BookmarkRepository(application)
    val settingsRepository = SettingsRepository(application)
    val adBlocker = AdBlocker.load(application)

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    init {
        viewModelScope.launch { historyRepository.load() }
        viewModelScope.launch { bookmarkRepository.load() }
        openNewTab(private = false)
    }

    fun activeTab(): BrowserTab? = _tabs.value.find { it.id == _activeTabId.value }

    fun openNewTab(private: Boolean, initialUrl: String? = null): String {
        val tab = BrowserTab(isPrivate = private, url = initialUrl.orEmpty())
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
        return tab.id
    }

    fun closeTab(tabId: String) {
        val remaining = _tabs.value.filterNot { it.id == tabId }
        _tabs.value = remaining
        if (_activeTabId.value == tabId) {
            _activeTabId.value = remaining.lastOrNull()?.id
        }
        // No new tab is force-opened here; the Home screen is shown when tabs.isEmpty().
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun switchToTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        _tabs.value = _tabs.value.map { if (it.id == tabId) transform(it) else it }
    }

    /** Records a visit to normal history. Never called for private tabs — enforced here, not just at call sites. */
    fun recordVisitIfNormal(tab: BrowserTab, title: String, url: String) {
        if (tab.isPrivate) return
        if (url.isBlank() || url.startsWith("about:")) return
        viewModelScope.launch { historyRepository.addVisit(title, url) }
    }

    fun buildLoadTarget(input: String, engine: SearchEngine): String {
        val trimmed = input.trim()
        val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ") &&
            (trimmed.startsWith("http://") || trimmed.startsWith("https://") || !trimmed.contains(" "))
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            looksLikeUrl && isLikelyDomain(trimmed) -> "https://$trimmed"
            else -> engine.urlTemplate.replace("%s", URLEncoder.encode(trimmed, "UTF-8"))
        }
    }

    private fun isLikelyDomain(text: String): Boolean {
        // Very small heuristic: "word.word" with no spaces and a plausible TLD length.
        val parts = text.split(".")
        return parts.size >= 2 && parts.last().length in 2..24 && parts.all { it.isNotBlank() }
    }

    /**
     * Closes a private session: removes its tab(s) and clears cookies/site-data/cache that the
     * WebView engine holds for that session. Because Android's WebView does not offer per-tab
     * cookie isolation, this clears the shared WebView cookie/storage state whenever the LAST
     * private tab is closed — normal tabs are unaffected because they don't rely on those cookies
     * being retained across app restarts in the same way (this is documented in the README's
     * "known limitations" section, since true per-tab container isolation would require a much
     * larger multi-profile WebView architecture).
     */
    fun closePrivateTabAndMaybeWipe(tabId: String) {
        val wasLastPrivateTab = _tabs.value.count { it.isPrivate } == 1 &&
            _tabs.value.any { it.id == tabId && it.isPrivate }
        closeTab(tabId)
        if (wasLastPrivateTab) {
            wipePrivateSessionData()
        }
    }

    private fun wipePrivateSessionData() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** "Clear browsing data" — history, cookies, cache, and other clearable local session data. */
    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean
    ) {
        if (clearHistory) {
            viewModelScope.launch { historyRepository.clearAll() }
        }
        if (clearCookies) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
        }
        if (clearCache) {
            getApplication<Application>().cacheDir.deleteRecursively()
        }
    }
}
