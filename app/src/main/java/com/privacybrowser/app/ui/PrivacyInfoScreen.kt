package com.privacybrowser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyInfoScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy information") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            InfoSection(
                "What this app stores on your device",
                "In normal browsing, this app keeps a local history of pages you visit, any " +
                    "bookmarks you save, your settings, and normal site data (cookies, cache) so " +
                    "sites work the way you'd expect. All of this stays on your device — the app " +
                    "does not send your browsing activity to Anthropic, the app developer, or any " +
                    "other server."
            )
            InfoSection(
                "What Private Mode does",
                "Pages you visit in a private tab are not added to your local browsing history. " +
                    "Cookies and site data are cleared once you close your last private tab (or " +
                    "close all tabs). Android's WebView shares one cookie store, so this also signs " +
                    "you out of sites in normal tabs. Files you download stay on your device, and " +
                    "appear in Android's own download list, until you delete them."
            )
            InfoSection(
                "What Private Mode does NOT protect against",
                "Private Mode only changes what this app stores on your device. It does not make " +
                    "you anonymous or invisible online. Websites you visit can still see your IP " +
                    "address and anything you type into or send to them. Your employer, school, " +
                    "or Wi-Fi network operator may still be able to see which sites you connect to. " +
                    "Your mobile carrier or internet service provider can typically see which " +
                    "domains you're contacting, even in Private Mode."
            )
            InfoSection(
                "Private Mode is not a VPN or Tor",
                "A VPN or Tor changes how your traffic is routed and can hide your IP address from " +
                    "the sites you visit and, to varying degrees, from your network operator. This " +
                    "browser's Private Mode does neither of those things — it only controls what's " +
                    "saved locally on your phone."
            )
            InfoSection(
                "Ad and tracker blocking",
                "The optional blocker stops requests to a list of known ad/tracker domains. It is " +
                    "not a complete or perfect list, and it does not block all forms of tracking " +
                    "(for example, tracking based on your account login on a site isn't affected " +
                    "by this feature)."
            )
            InfoSection(
                "Clearing browsing data",
                "\"Clear browsing data\" deletes information stored locally on your device — it " +
                    "cannot remove information already collected by websites you've visited, or by " +
                    "networks your traffic passed through."
            )
            InfoSection(
                "App lock",
                "If you set a PIN, this app never stores the PIN itself — only a securely hashed, " +
                    "salted representation of it, used to verify future unlock attempts. Biometric " +
                    "unlock (where available) uses Android's own biometric system; this app never " +
                    "sees or stores your fingerprint or face data. A short PIN can only offer " +
                    "limited protection: it keeps casual users out, but it is not designed to " +
                    "stop someone who can extract data from a rooted or compromised device."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
