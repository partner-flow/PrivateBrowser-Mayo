package com.privacybrowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacybrowser.app.browser.DownloadsManager
import com.privacybrowser.app.browser.SystemDownloadEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadsManager: DownloadsManager,
    onBack: () -> Unit,
    onOpenDownload: (id: Long) -> Unit
) {
    var downloads by remember { mutableStateOf<List<SystemDownloadEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Simple polling refresh while this screen is visible, so in-progress downloads update.
    // The query itself hits DownloadManager's ContentProvider, so it's pushed off the main
    // thread rather than run inline in the Compose/UI coroutine.
    LaunchedEffect(Unit) {
        while (true) {
            downloads = withContext(Dispatchers.IO) { downloadsManager.queryDownloads() }
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Files you download from the web will show up here.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(downloads, key = { it.id }) { entry ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                when {
                                    entry.isFailed -> Icons.Filled.Error
                                    entry.isComplete -> Icons.Filled.CheckCircle
                                    else -> Icons.Filled.Download
                                },
                                contentDescription = null
                            )
                        },
                        headlineContent = { Text(entry.title, maxLines = 1) },
                        supportingContent = {
                            Column {
                                when {
                                    entry.isRunning -> {
                                        Text("Downloading… ${entry.progressPercent}%")
                                        LinearProgressIndicator(
                                            progress = { entry.progressPercent / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    entry.isFailed -> Text("Failed — tap to try again from the page")
                                    entry.isComplete -> Text("Complete")
                                    else -> Text("Pending")
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) { downloadsManager.removeDownload(entry.id) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        },
                        modifier = Modifier.clickable(enabled = entry.isComplete && entry.localUri != null) {
                            onOpenDownload(entry.id)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
