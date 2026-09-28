package com.privacybrowser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.privacybrowser.app.browser.BrowserViewModel
import com.privacybrowser.app.model.SearchEngine
import com.privacybrowser.app.security.AppLockManager
import com.privacybrowser.app.ui.*
import com.privacybrowser.app.ui.theme.PrivateBrowserTheme
import kotlinx.coroutines.launch

/**
 * Uses FragmentActivity (not plain ComponentActivity) because androidx.biometric's
 * BiometricPrompt requires a FragmentActivity host.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockManager = AppLockManager(applicationContext)

        setContent {
            val themeMode by viewModel.settingsRepository.themeMode.collectAsState(initial = "system")
            val forceDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> null
            }

            PrivateBrowserTheme(forceDark = forceDark) {
                AppRoot(viewModel = viewModel, appLockManager = appLockManager, activity = this)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: BrowserViewModel, appLockManager: AppLockManager, activity: FragmentActivity) {
    val appLockEnabled by viewModel.settingsRepository.appLockEnabled.collectAsState(initial = false)
    val biometricEnabled by viewModel.settingsRepository.biometricEnabled.collectAsState(initial = false)

    // Gate the whole app behind the lock screen on cold start / resume when app lock is on.
    var unlocked by remember { mutableStateOf(!appLockEnabled) }
    LaunchedEffect(appLockEnabled) {
        if (appLockEnabled && appLockManager.isPinSet()) unlocked = false
        if (!appLockEnabled) unlocked = true
    }

    if (!unlocked) {
        val biometricAvailable = remember {
            BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        }
        LockScreen(
            mode = LockScreenMode.UNLOCK,
            appLockManager = appLockManager,
            biometricAvailableAndEnabled = biometricEnabled && biometricAvailable,
            onUnlocked = { unlocked = true },
            onPinSet = {},
            onRequestBiometric = {
                showBiometricPrompt(activity) { unlocked = true }
            }
        )
    } else {
        BrowserNavHost(viewModel = viewModel, appLockManager = appLockManager)
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Private Browser")
        .setNegativeButtonText("Use PIN instead")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info)
}

@Composable
private fun BrowserNavHost(viewModel: BrowserViewModel, appLockManager: AppLockManager) {
    val navController: NavHostController = rememberNavController()
    val searchEngine by viewModel.settingsRepository.searchEngine.collectAsState(initial = SearchEngine.DUCKDUCKGO)
    val adBlockEnabled by viewModel.settingsRepository.adBlockEnabled.collectAsState(initial = true)
    val jsEnabled by viewModel.settingsRepository.javascriptEnabled.collectAsState(initial = true)
    val cookiesEnabled by viewModel.settingsRepository.cookiesEnabled.collectAsState(initial = true)

    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                viewModel = viewModel,
                searchEngine = searchEngine,
                adBlockEnabled = adBlockEnabled,
                javascriptEnabled = jsEnabled,
                cookiesEnabled = cookiesEnabled,
                onOpenHistory = { navController.navigate("history") },
                onOpenBookmarks = { navController.navigate("bookmarks") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    viewModel.openNewTab(private = false, initialUrl = url)
                    navController.popBackStack("browser", inclusive = false)
                }
            )
        }
        composable("bookmarks") {
            BookmarksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    viewModel.openNewTab(private = false, initialUrl = url)
                    navController.popBackStack("browser", inclusive = false)
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                appLockManager = appLockManager,
                onBack = { navController.popBackStack() },
                onOpenPrivacyInfo = { navController.navigate("privacy_info") },
                onSetPin = { navController.navigate("set_pin") }
            )
        }
        composable("privacy_info") {
            PrivacyInfoScreen(onBack = { navController.popBackStack() })
        }
        composable("set_pin") {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LockScreen(
                mode = LockScreenMode.SETUP,
                appLockManager = appLockManager,
                biometricAvailableAndEnabled = false,
                onUnlocked = {},
                onPinSet = {
                    scope.launch { viewModel.settingsRepository.setAppLockEnabled(true) }
                    navController.popBackStack()
                },
                onRequestBiometric = {}
            )
        }
    }
}
