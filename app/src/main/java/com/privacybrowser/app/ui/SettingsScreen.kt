package com.privacybrowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.privacybrowser.app.browser.BrowserViewModel
import com.privacybrowser.app.model.SearchEngine
import com.privacybrowser.app.security.AppLockManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    appLockManager: AppLockManager,
    onBack: () -> Unit,
    onOpenPrivacyInfo: () -> Unit,
    onSetPin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val searchEngine by viewModel.settingsRepository.searchEngine.collectAsState(initial = SearchEngine.DUCKDUCKGO)
    val adBlockEnabled by viewModel.settingsRepository.adBlockEnabled.collectAsState(initial = true)
    val jsEnabled by viewModel.settingsRepository.javascriptEnabled.collectAsState(initial = true)
    val cookiesEnabled by viewModel.settingsRepository.cookiesEnabled.collectAsState(initial = true)
    val appLockEnabled by viewModel.settingsRepository.appLockEnabled.collectAsState(initial = false)
    val biometricEnabled by viewModel.settingsRepository.biometricEnabled.collectAsState(initial = false)

    val themeMode by viewModel.settingsRepository.themeMode.collectAsState(initial = "system")
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            SettingsSectionTitle("Search")
            SettingsRow(
                title = "Search engine",
                subtitle = searchEngine.label,
                onClick = { showSearchEngineDialog = true }
            )

            SettingsRow(
                title = "Theme",
                subtitle = when (themeMode) { "light" -> "Light"; "dark" -> "Dark"; else -> "Follow system" },
                onClick = { showThemeDialog = true }
            )

            SettingsSectionTitle("Privacy")
            SettingsSwitchRow(
                title = "Block ads & trackers",
                subtitle = "Blocks known ad/tracker domains. Not 100% — some trackers may still get through.",
                checked = adBlockEnabled,
                onCheckedChange = { scope.launch { viewModel.settingsRepository.setAdBlockEnabled(it) } }
            )
            SettingsSwitchRow(
                title = "JavaScript",
                subtitle = "Most sites need this on to work correctly.",
                checked = jsEnabled,
                onCheckedChange = { scope.launch { viewModel.settingsRepository.setJavascriptEnabled(it) } }
            )
            SettingsSwitchRow(
                title = "Cookies & site data",
                subtitle = "Lets sites remember you're logged in, etc.",
                checked = cookiesEnabled,
                onCheckedChange = { scope.launch { viewModel.settingsRepository.setCookiesEnabled(it) } }
            )
            SettingsRow(
                title = "Clear browsing data",
                subtitle = "History, cookies, and cache",
                onClick = { showClearDataDialog = true }
            )
            SettingsRow(
                title = "Privacy information",
                subtitle = "What Private Mode does and doesn't protect against",
                onClick = onOpenPrivacyInfo
            )

            SettingsSectionTitle("App lock")
            SettingsSwitchRow(
                title = "Require PIN to open app",
                subtitle = if (appLockManager.isPinSet()) "PIN is set" else "No PIN set yet",
                checked = appLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !appLockManager.isPinSet()) {
                        onSetPin()
                    } else {
                        scope.launch { viewModel.settingsRepository.setAppLockEnabled(enabled) }
                    }
                }
            )
            if (appLockManager.isPinSet()) {
                SettingsRow(title = "Change PIN", onClick = onSetPin)
            }
            SettingsSwitchRow(
                title = "Allow biometric unlock",
                subtitle = "Use fingerprint/face in addition to your PIN, if your device supports it.",
                checked = biometricEnabled,
                enabled = appLockEnabled,
                onCheckedChange = { scope.launch { viewModel.settingsRepository.setBiometricEnabled(it) } }
            )

            SettingsSectionTitle("About")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") }
            )
            SettingsRow(
                title = "Open-source licenses",
                subtitle = "Libraries this app is built with",
                onClick = { showLicensesDialog = true }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == key,
                                onClick = {
                                    scope.launch { viewModel.settingsRepository.setThemeMode(key) }
                                    showThemeDialog = false
                                }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "Private Browser is built with these libraries, all licensed under the " +
                        "Apache License 2.0:\n\n" +
                        "• AndroidX Core, Lifecycle, Activity, Navigation, DataStore, Biometric\n" +
                        "• Jetpack Compose (UI, Foundation, Material 3, Material Icons)\n" +
                        "• Kotlin and kotlinx.coroutines\n\n" +
                        "Full license text: https://www.apache.org/licenses/LICENSE-2.0"
                )
            },
            confirmButton = { TextButton(onClick = { showLicensesDialog = false }) { Text("Close") } }
        )
    }

    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Search engine") },
            text = {
                Column {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = engine == searchEngine,
                                onClick = {
                                    scope.launch { viewModel.settingsRepository.setSearchEngine(engine) }
                                    showSearchEngineDialog = false
                                }
                            )
                            Text(engine.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearchEngineDialog = false }) { Text("Close") } }
        )
    }

    if (showClearDataDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCookies by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear browsing data") },
            text = {
                Column {
                    Text("This can't be undone. Note: this only clears data stored on this device — it doesn't remove information already sent to websites, ISPs, or other third parties.")
                    Spacer(Modifier.height(8.dp))
                    CheckRow("History", clearHistory) { clearHistory = it }
                    CheckRow("Cookies & site data", clearCookies) { clearCookies = it }
                    CheckRow("Cache", clearCache) { clearCache = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowsingData(clearHistory, clearCookies, clearCache)
                    showClearDataDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
    )
}
