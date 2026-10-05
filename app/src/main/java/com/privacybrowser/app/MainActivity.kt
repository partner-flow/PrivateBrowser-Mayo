package com.privacybrowser.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.privacybrowser.app.browser.BrowserViewModel
import com.privacybrowser.app.model.SearchEngine
import com.privacybrowser.app.security.AppLockManager
import com.privacybrowser.app.ui.*
import com.privacybrowser.app.ui.theme.PrivateBrowserTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Uses FragmentActivity (not plain ComponentActivity) because androidx.biometric's
 * BiometricPrompt requires a FragmentActivity host.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var appLockManager: AppLockManager

    // Only ever triggered on API 28 and below; API 29+ downloads need no runtime permission
    // (scoped storage covers the public Downloads collection automatically).
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.retryPendingDownloadAfterPermissionGranted()
        else viewModel.clearPendingDownloadPermissionRequest()
    }

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

            val pendingDownloadPermission by viewModel.pendingDownloadNeedingPermission.collectAsState()
            LaunchedEffect(pendingDownloadPermission) {
                if (pendingDownloadPermission != null) {
                    storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            PrivateBrowserTheme(forceDark = forceDark) {
                AppRoot(viewModel = viewModel, appLockManager = appLockManager, activity = this)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: BrowserViewModel, appLockManager: AppLockManager, activity: FragmentActivity) {
    // Nullable-until-loaded so we never briefly render the browser before we actually know
    // whether app lock is on — closes a real "flash of unlocked content" gap on cold start.
    val appLockEnabled by remember { viewModel.settingsRepository.appLockEnabled.map { enabled -> enabled as Boolean? } }
        .collectAsState(initial = null)
    val biometricEnabled by viewModel.settingsRepository.biometricEnabled.collectAsState(initial = false)

    var unlocked by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    // True only while our own BiometricPrompt dialog is up, so the ON_STOP relock below doesn't
    // treat that system dialog as "app backgrounded".
    var biometricPromptShowing by remember { mutableStateOf(false) }

    LaunchedEffect(appLockEnabled) {
        val enabled = appLockEnabled ?: return@LaunchedEffect
        if (!initialized) {
            unlocked = !(enabled && appLockManager.isPinSet())
            initialized = true
        }
    }

    // Re-lock whenever the app is actually backgrounded (not just covered by our own
    // biometric dialog), so app lock protects against "someone else picks up the phone",
    // not just cold start.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                appLockEnabled == true &&
                appLockManager.isPinSet() &&
                !biometricPromptShowing
            ) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !initialized -> {
            // Settings haven't loaded yet; show a blank themed surface rather than any content.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        !unlocked -> {
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
                    biometricPromptShowing = true
                    showBiometricPrompt(
                        activity = activity,
                        onSuccess = { biometricPromptShowing = false; unlocked = true },
                        onDismissed = { biometricPromptShowing = false }
                    )
                }
            )
        }
        else -> BrowserNavHost(viewModel = viewModel, appLockManager = appLockManager)
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit, onDismissed: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onDismissed()
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
                onOpenSettings = { navController.navigate("settings") },
                onOpenDownloads = { navController.navigate("downloads") }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    viewModel.openNewTab(isPrivate = false, initialUrl = url)
                    navController.popBackStack("browser", inclusive = false)
                }
            )
        }
        composable("bookmarks") {
            BookmarksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    viewModel.openNewTab(isPrivate = false, initialUrl = url)
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
        composable("downloads") {
            val context = androidx.compose.ui.platform.LocalContext.current
            DownloadsScreen(
                downloadsManager = viewModel.downloadsManager,
                onBack = { navController.popBackStack() },
                onOpenDownload = { id ->
                    val uri = viewModel.downloadsManager.getOpenUri(id)
                    if (uri != null) {
                        runCatching {
                            val mime = context.contentResolver.getType(uri)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime ?: "*/*")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            )
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
