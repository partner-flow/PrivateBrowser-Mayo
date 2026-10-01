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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val bookmarks by viewModel.bookmarkRepository.bookmarks.collectAsState()
    val bookmarkCount = bookmarks.size
    var showTabSwitcher by remember { mutableStateOf(false) }
    var addressText by remember(activeTabId) { mutableStateOf(tabs.find { it.id == activeTabId }?.url.orEmpty()) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var showAllClearConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadMessage) {
        downloadMessage?.let {
            snackbarHostState.showSnackbar(it)
            downloadMessage = null
        }
    }

    val activeTab = tabs.find { it.id == activeTabId }
    // Holds the live WebView for the active tab so the address bar's back/forward/reload
    // buttons can drive it directly (WebView navigation state lives in the WebView itself).
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // System back: close the menu/switcher first, then go back in the page, then return to the
    // tab's home screen; only when none of those apply does back leave the app.
    androidx.activity.compose.BackHandler(
        enabled = menuExpanded || showTabSwitcher || activeTab?.canGoBack == true ||
            (activeTab != null && activeTab.url.isNotBlank())
    ) {
        when {
            menuExpanded -> menuExpanded = false
            showTabSwitcher -> showTabSwitcher = false
            activeTab?.canGoBack == true && webViewRef.value != null -> webViewRef.value?.goBack()
            else -> activeTab?.let { tab ->
                viewModel.updateTab(tab.id) { it.copy(url = "", loadError = null, canGoBack = false, canGoForward = false, isLoading = false) }
                addressText = ""
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        viewModel.openNewTab(isPrivate = false, initialUrl = target)
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
                onMenuClick = { menuExpanded = true },
                onAllClearClick = { showAllClearConfirm = true }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                activeTab == null -> HomeEmptyState(onNewTab = { viewModel.openNewTab(isPrivate = false) })
                activeTab.url.isBlank() -> HomeContent(
                    isPrivate = activeTab.isPrivate,
                    bookmarkCount = bookmarkCount,
                    onNewTab = { viewModel.openNewTab(isPrivate = false) },
                    onNewPrivateTab = { viewModel.openNewTab(isPrivate = true) },
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenHistory = onOpenHistory
                )
                activeTab.loadError != null -> ErrorScreen(
                    error = activeTab.loadError!!,
                    onRetry = {
                        // Clearing the error recreates the WebView, which reloads the tab's URL.
                        viewModel.updateTab(activeTab.id) { it.copy(loadError = null) }
                    }
                )
                else -> key(activeTab.id) { BrowserWebView(
                    tab = activeTab,
                    adBlockEnabled = adBlockEnabled,
                    adBlocker = viewModel.adBlocker,
                    javascriptEnabled = javascriptEnabled,
                    cookiesEnabled = cookiesEnabled,
                    onTabUpdate = { transform -> viewModel.updateTab(activeTab.id, transform) },
                    onPageVisited = { title, url -> viewModel.recordVisitIfNormal(activeTab, title, url) },
                    onWebViewReady = { webViewRef.value = it },
                    onDownloadRequested = { url, userAgent, contentDisposition, mimeType ->
                        downloadMessage = viewModel.requestDownload(url, userAgent, contentDisposition, mimeType)
                    }
                ) }
            }

            if (menuExpanded) {
                BrowserMenu(
                    isBookmarked = activeTab?.url?.let { viewModel.bookmarkRepository.isBookmarked(it) } ?: false,
                    onDismiss = { menuExpanded = false },
                    onHistory = { menuExpanded = false; onOpenHistory() },
                    onBookmarks = { menuExpanded = false; onOpenBookmarks() },
                    onSettings = { menuExpanded = false; onOpenSettings() },
                    onDownloads = { menuExpanded = false; onOpenDownloads() },
                    onNewTab = { menuExpanded = false; viewModel.openNewTab(isPrivate = false) },
                    onNewPrivateTab = { menuExpanded = false; viewModel.openNewTab(isPrivate = true) },
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
            onNewTab = { showTabSwitcher = false; viewModel.openNewTab(isPrivate = false) },
            onNewPrivateTab = { showTabSwitcher = false; viewModel.openNewTab(isPrivate = true) },
            onDismiss = { showTabSwitcher = false }
        )
    }

    if (showAllClearConfirm) {
        AlertDialog(
            onDismissRequest = { showAllClearConfirm = false },
            title = { Text("Clear everything?") },
            text = { Text("This will close all tabs and clear browsing data.") },
            confirmButton = {
                TextButton(onClick = {
                    showAllClearConfirm = false
                    menuExpanded = false
                    showTabSwitcher = false
                    addressText = ""
                    isEditingAddress = false
                    viewModel.allClear()
                }) { Text("\uD83D\uDD25 All Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showAllClearConfirm = false }) { Text("Cancel") }
            }
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
    onMenuClick: () -> Unit,
    onAllClearClick: () -> Unit
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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Go
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onGo = { if (text.isNotBlank()) onSubmit(text) }
                        ),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                    )

                    IconButton(
                        onClick = onAllClearClick,
                        modifier = Modifier.semantics { contentDescription = "All Clear" }
                    ) {
                        Text("\uD83D\uDD25", style = MaterialTheme.typography.titleMedium)
                    }
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

/**
 * The tab's home screen. Deliberately has NO text field of its own — typing or searching always
 * happens in the top address bar, never here. This only offers a few quick actions; it stays
 * static (no per-keystroke recomposition) since it only reacts to isPrivate/bookmarkCount.
 */
@Composable
private fun HomeContent(
    isPrivate: Boolean,
    bookmarkCount: Int,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit
) {
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
            else "Type a search or a web address in the bar above.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isPrivate) {
                FilledTonalButton(onClick = onNewTab) { Text("New tab") }
            } else {
                FilledTonalButton(onClick = onNewPrivateTab) { Text("New private tab") }
            }
            if (!isPrivate && bookmarkCount > 0) {
                OutlinedButton(onClick = onOpenBookmarks) { Text("Bookmarks") }
            }
            if (!isPrivate) {
                OutlinedButton(onClick = onOpenHistory) { Text("History") }
            }
        }
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
    onWebViewReady: (WebView?) -> Unit,
    onDownloadRequested: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) -> Unit
) {
    val context = LocalContext.current
    // The WebView is created once per tab, so anything that can change later (settings, callbacks)
    // is read through these always-current holders instead of being captured at creation time.
    val currentAdBlockEnabled by rememberUpdatedState(adBlockEnabled)
    val currentOnTabUpdate by rememberUpdatedState(onTabUpdate)
    val currentOnPageVisited by rememberUpdatedState(onPageVisited)
    val currentOnDownload by rememberUpdatedState(onDownloadRequested)
    val currentOnReady by rememberUpdatedState(onWebViewReady)
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    // Pause/resume the WebView with the host lifecycle so background pages don't keep running
    // (and don't keep burning battery/CPU) while the app is backgrounded.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> webViewHolder[0]?.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> webViewHolder[0]?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val webView = WebView(ctx)
            webViewHolder[0] = webView
            webView.apply {
                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    currentOnDownload(url, userAgent, contentDisposition, mimeType)
                }
                settings.apply {
                    javaScriptEnabled = javascriptEnabled
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    // Never let web content read local files or app content providers.
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    // Private tabs never read from or write to the HTTP cache.
                    cacheMode = if (tab.isPrivate) android.webkit.WebSettings.LOAD_NO_CACHE
                    else android.webkit.WebSettings.LOAD_DEFAULT
                }
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(cookiesEnabled)
                cookieManager.setAcceptThirdPartyCookies(this, false)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        currentOnTabUpdate { it.copy(progress = newProgress, isLoading = newProgress in 1..99) }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        currentOnTabUpdate { it.copy(title = title ?: it.title) }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return true
                        return when (uri.scheme?.lowercase()) {
                            "http", "https" -> false
                            // Hand simple mail/phone links to the system; block every other scheme
                            // (file:, javascript:, intent:, content: ...) instead of loading it.
                            "mailto", "tel" -> {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                true
                            }
                            else -> true
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (currentAdBlockEnabled && request != null && !request.isForMainFrame &&
                            adBlocker.shouldBlock(request)
                        ) {
                            return adBlocker.blockedResponse()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        currentOnTabUpdate { it.copy(isLoading = true, loadError = null, url = url ?: it.url) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        currentOnTabUpdate {
                            it.copy(
                                isLoading = false,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }
                        if (url != null) currentOnPageVisited(view?.title ?: url, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            currentOnTabUpdate {
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
                        currentOnTabUpdate {
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
            currentOnReady(webView)
            webView
        },
        update = { webView ->
            // Keep live-changeable settings in sync with the Settings screen.
            webView.settings.javaScriptEnabled = javascriptEnabled
            android.webkit.CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
            if (tab.url.isNotBlank() && webView.url != tab.url) {
                webView.loadUrl(tab.url)
            }
        },
        onRelease = { webView ->
            // Free the WebView's native resources as soon as the tab leaves the screen.
            webViewHolder[0] = null
            currentOnReady(null)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.setDownloadListener(null)
            webView.destroy()
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
    onDownloads: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(text = { Text(if (isBookmarked) "Remove bookmark" else "Add bookmark") }, onClick = onToggleBookmark, leadingIcon = { Icon(if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, null) })
            DropdownMenuItem(text = { Text("New tab") }, onClick = onNewTab, leadingIcon = { Icon(Icons.Filled.Add, null) })
            DropdownMenuItem(text = { Text("New private tab") }, onClick = onNewPrivateTab, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) })
            DropdownMenuItem(text = { Text("History") }, onClick = onHistory, leadingIcon = { Icon(Icons.Filled.History, null) })
            DropdownMenuItem(text = { Text("Bookmarks") }, onClick = onBookmarks, leadingIcon = { Icon(Icons.Filled.Star, null) })
            DropdownMenuItem(text = { Text("Downloads") }, onClick = onDownloads, leadingIcon = { Icon(Icons.Filled.Download, null) })
            DropdownMenuItem(text = { Text("Settings") }, onClick = onSettings, leadingIcon = { Icon(Icons.Filled.Settings, null) })
        }
    }
}
