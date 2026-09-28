@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.privacybrowser.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import com.privacybrowser.app.browser.BrowserViewModel
import com.privacybrowser.app.model.BrowserTab
import com.privacybrowser.app.model.LoadError
import com.privacybrowser.app.model.SearchEngine
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    searchEngine: SearchEngine,
    adBlockEnabled: Boolean,
    javascriptEnabled: Boolean,
    cookiesEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    var showTabSwitcher by remember { mutableStateOf(false) }
    var addressText by remember(activeTabId) { mutableStateOf(tabs.find { it.id == activeTabId }?.url.orEmpty()) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val activeTab = tabs.find { it.id == activeTabId }
    // Holds the live WebView for the active tab so the address bar's back/forward/reload
    // buttons can drive it directly (WebView navigation state lives in the WebView itself).
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(activeTabId) { webViewRef.value = null }

    Scaffold(
        topBar = {
            AddressBar(
                text = if (isEditingAddress) addressText else (activeTab?.url ?: addressText),
                isPrivate = activeTab?.isPrivate == true,
                isLoading = activeTab?.isLoading == true,
                progress = activeTab?.progress ?: 0,
                onTextChange = {
                    addressText = it
                    isEditingAddress = true
                },
                onSubmit = { input ->
                    isEditingAddress = false
                    val target = viewModel.buildLoadTarget(input, searchEngine)
                    activeTab?.let { tab ->
                        viewModel.updateTab(tab.id) { it.copy(url = target) }
                    } ?: run {
                        viewModel.openNewTab(private = false, initialUrl = target)
                        addressText = target
                    }
                },
                canGoBack = activeTab?.canGoBack == true,
                canGoForward = activeTab?.canGoForward == true,
                onBack = { webViewRef.value?.takeIf { it.canGoBack() }?.goBack() },
                onForward = { webViewRef.value?.takeIf { it.canGoForward() }?.goForward() },
                onReload = { webViewRef.value?.reload() },
                onHome = {
                    activeTab?.let { viewModel.updateTab(it.id) { t -> t.copy(url = "") } }
                    addressText = ""
                    isEditingAddress = false
                },
                tabCount = tabs.size,
                onTabsClick = { showTabSwitcher = true },
                onMenuClick = { menuExpanded = true }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                activeTab == null -> HomeEmptyState(onNewTab = { viewModel.openNewTab(private = false) })
                activeTab.url.isBlank() -> HomeContent(
                    isPrivate = activeTab.isPrivate,
                    onSubmitQuery = { query ->
                        val target = viewModel.buildLoadTarget(query, searchEngine)
                        viewModel.updateTab(activeTab.id) { it.copy(url = target) }
                        addressText = target
                    }
                )
                activeTab.loadError != null -> ErrorScreen(
                    error = activeTab.loadError!!,
                    onRetry = {
                        val url = activeTab.url
                        viewModel.updateTab(activeTab.id) { it.copy(loadError = null, url = "") }
                        viewModel.updateTab(activeTab.id) { it.copy(url = url) }
                    }
                )
                else -> BrowserWebView(
                    tab = activeTab,
                    adBlockEnabled = adBlockEnabled,
                    adBlocker = viewModel.adBlocker,
                    javascriptEnabled = javascriptEnabled,
                    cookiesEnabled = cookiesEnabled,
                    onTabUpdate = { transform -> viewModel.updateTab(activeTab.id, transform) },
                    onPageVisited = { title, url -> viewModel.recordVisitIfNormal(activeTab, title, url) },
                    onWebViewReady = { webViewRef.value = it }
                )
            }

            if (menuExpanded) {
                BrowserMenu(
                    isBookmarked = activeTab?.url?.let { viewModel.bookmarkRepository.isBookmarked(it) } ?: false,
                    onDismiss = { menuExpanded = false },
                    onHistory = { menuExpanded = false; onOpenHistory() },
                    onBookmarks = { menuExpanded = false; onOpenBookmarks() },
                    onSettings = { menuExpanded = false; onOpenSettings() },
                    onNewTab = { menuExpanded = false; viewModel.openNewTab(private = false) },
                    onNewPrivateTab = { menuExpanded = false; viewModel.openNewTab(private = true) },
                    onToggleBookmark = {
                        menuExpanded = false
                        activeTab?.let { tab ->
                            if (tab.url.isNotBlank()) {
                                viewModel.viewModelScope.launch {
                                    if (viewModel.bookmarkRepository.isBookmarked(tab.url)) {
                                        viewModel.bookmarkRepository.remove(tab.url)
                                    } else {
                                        viewModel.bookmarkRepository.add(tab.title, tab.url)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showTabSwitcher) {
        TabSwitcherSheet(
            tabs = tabs,
            activeTabId = activeTabId,
            onSelect = { showTabSwitcher = false; viewModel.switchToTab(it) },
            onClose = { tabId ->
                val tab = tabs.find { it.id == tabId }
                if (tab?.isPrivate == true) viewModel.closePrivateTabAndMaybeWipe(tabId)
                else viewModel.closeTab(tabId)
            },
            onCloseAll = { viewModel.closeAllTabs(); showTabSwitcher = false },
            onNewTab = { showTabSwitcher = false; viewModel.openNewTab(private = false) },
            onNewPrivateTab = { showTabSwitcher = false; viewModel.openNewTab(private = true) },
            onDismiss = { showTabSwitcher = false }
        )
    }
}

@Composable
private fun AddressBar(
    text: String,
    isPrivate: Boolean,
    isLoading: Boolean,
    progress: Int,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    tabCount: Int,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column {
        Surface(
            color = if (isPrivate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column {
                if (isPrivate) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Private Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onHome) { Icon(Icons.Filled.Home, contentDescription = "Home") }
                    IconButton(onClick = onBack, enabled = canGoBack) { Icon(ArrowBack, contentDescription = "Back") }
                    IconButton(onClick = onForward, enabled = canGoForward) { Icon(ArrowForward, contentDescription = "Forward") }
                    IconButton(onClick = onReload) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }

                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Search or enter address") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { onSubmit(text) }
                        ),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                    )

                    IconButton(onClick = onTabsClick) {
                        BadgedBox(badge = { if (tabCount > 0) Badge { Text(tabCount.toString()) } }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Tabs")
                        }
                    }
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                }
            }
        }
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeContent(isPrivate: Boolean, onSubmitQuery: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Public,
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isPrivate) "Private tab" else "Private Browser",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isPrivate) "This tab won't be added to your history."
            else "Search the web or enter a website address.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search or enter address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (query.isNotBlank()) onSubmitQuery(query) }
            )
        )
    }
}

@Composable
private fun HomeEmptyState(onNewTab: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No open tabs", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewTab) { Text("New tab") }
    }
}

@Composable
private fun ErrorScreen(error: LoadError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Page couldn't load", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            error.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    tab: BrowserTab,
    adBlockEnabled: Boolean,
    adBlocker: com.privacybrowser.app.browser.AdBlocker,
    javascriptEnabled: Boolean,
    cookiesEnabled: Boolean,
    onTabUpdate: ((BrowserTab) -> BrowserTab) -> Unit,
    onPageVisited: (String, String) -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                onWebViewReady(this)
                settings.javaScriptEnabled = javascriptEnabled
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.cacheMode = if (tab.isPrivate) WebView.LOAD_NO_CACHE else WebView.LOAD_DEFAULT

                android.webkit.CookieManager.getInstance().apply {
                    setAcceptCookie(cookiesEnabled)
                    setAcceptThirdPartyCookies(this@apply, false)
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onTabUpdate { it.copy(progress = newProgress, isLoading = newProgress in 1..99) }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        onTabUpdate { it.copy(title = title ?: it.title) }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (adBlockEnabled && request != null && !request.isForMainFrame && adBlocker.shouldBlock(request)) {
                            return adBlocker.blockedResponse()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onTabUpdate { it.copy(isLoading = true, loadError = null, url = url ?: it.url) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onTabUpdate {
                            it.copy(
                                isLoading = false,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }
                        if (url != null) onPageVisited(view?.title ?: url, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onTabUpdate {
                                it.copy(
                                    isLoading = false,
                                    loadError = LoadError(
                                        description = error?.description?.toString()
                                            ?: "This page couldn't load. Check your connection and try again.",
                                        failingUrl = request.url?.toString()
                                    )
                                )
                            }
                        }
                    }

                    // Never silently accept invalid certificates.
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.cancel()
                        onTabUpdate {
                            it.copy(
                                isLoading = false,
                                loadError = LoadError(
                                    description = "This connection isn't secure (invalid certificate). The page was blocked for your safety.",
                                    failingUrl = error?.url
                                )
                            )
                        }
                    }
                }

                if (tab.url.isNotBlank()) loadUrl(tab.url)
            }
        },
        update = { webView ->
            if (tab.url.isNotBlank() && webView.url != tab.url) {
                webView.loadUrl(tab.url)
            }
        }
    )
}

@Composable
private fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tabs (${tabs.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onCloseAll, enabled = tabs.isNotEmpty()) { Text("Close all") }
            }
            Spacer(Modifier.height(8.dp))
            if (tabs.isEmpty()) {
                Text("No open tabs", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(tabs, key = { it.id }) { tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (tab.id == activeTabId) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (tab.isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Public,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .then(Modifier.background(androidx.compose.ui.graphics.Color.Transparent))
                            ) {
                                Text(
                                    tab.title.ifBlank { "New Tab" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                if (tab.url.isNotBlank()) {
                                    Text(tab.url, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                            IconButton(onClick = { onSelect(tab.id) }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Switch to tab")
                            }
                            IconButton(onClick = { onClose(tab.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close tab")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNewTab, modifier = Modifier.weight(1f)) { Text("New tab") }
                OutlinedButton(onClick = onNewPrivateTab, modifier = Modifier.weight(1f)) { Text("New private tab") }
            }
        }
    }
}

@Composable
private fun BrowserMenu(
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(text = { Text(if (isBookmarked) "Remove bookmark" else "Add bookmark") }, onClick = onToggleBookmark, leadingIcon = { Icon(if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, null) })
            DropdownMenuItem(text = { Text("New tab") }, onClick = onNewTab, leadingIcon = { Icon(Icons.Filled.Add, null) })
            DropdownMenuItem(text = { Text("New private tab") }, onClick = onNewPrivateTab, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) })
            DropdownMenuItem(text = { Text("History") }, onClick = onHistory, leadingIcon = { Icon(Icons.Filled.History, null) })
            DropdownMenuItem(text = { Text("Bookmarks") }, onClick = onBookmarks, leadingIcon = { Icon(Icons.Filled.Star, null) })
            DropdownMenuItem(text = { Text("Settings") }, onClick = onSettings, leadingIcon = { Icon(Icons.Filled.Settings, null) })
        }
    }
}
