package com.privacybrowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privacybrowser.app.browser.BrowserViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: BrowserViewModel, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val history by viewModel.historyRepository.history.collectAsState()
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { showClearConfirm = true }, enabled = history.isNotEmpty()) {
                        Text("Clear all")
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("No history yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pages you visit in normal (non-private) tabs will show up here.",
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(history, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 1) },
                        supportingContent = {
                            Column {
                                Text(entry.url, maxLines = 1)
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(entry.visitedAtEpochMs)),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { viewModel.historyRepository.deleteEntry(entry.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                            }
                        },
                        modifier = Modifier.openOnClick(entry.url, onOpenUrl)
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every entry from your browsing history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.historyRepository.clearAll() }
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

private fun Modifier.openOnClick(url: String, onOpenUrl: (String) -> Unit): Modifier =
    this.then(Modifier.clickable { onOpenUrl(url) })
