package com.privacybrowser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.privacybrowser.app.security.AppLockManager

/**
 * @param mode SETUP = user is choosing a new PIN (Settings). UNLOCK = app-start gate.
 */
enum class LockScreenMode { SETUP, UNLOCK }

@Composable
fun LockScreen(
    mode: LockScreenMode,
    appLockManager: AppLockManager,
    biometricAvailableAndEnabled: Boolean,
    onUnlocked: () -> Unit,
    onPinSet: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var awaitingConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (mode == LockScreenMode.SETUP) {
                if (awaitingConfirm) "Confirm your PIN" else "Choose a PIN"
            } else "Enter your PIN",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = if (awaitingConfirm) confirmPin else pin,
            onValueChange = {
                if (it.length <= 8 && it.all(Char::isDigit)) {
                    if (awaitingConfirm) confirmPin = it else pin = it
                }
                error = null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            label = { Text("PIN") }
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            when (mode) {
                LockScreenMode.SETUP -> {
                    if (!awaitingConfirm) {
                        if (pin.length < 4) {
                            error = "Use at least 4 digits"
                        } else {
                            awaitingConfirm = true
                        }
                    } else {
                        if (confirmPin != pin) {
                            error = "PINs don't match"
                            confirmPin = ""
                        } else {
                            appLockManager.setPin(pin)
                            onPinSet()
                        }
                    }
                }
                LockScreenMode.UNLOCK -> {
                    if (appLockManager.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = "Incorrect PIN"
                        pin = ""
                    }
                }
            }
        }) {
            Text(
                when {
                    mode == LockScreenMode.SETUP && !awaitingConfirm -> "Continue"
                    mode == LockScreenMode.SETUP -> "Save PIN"
                    else -> "Unlock"
                }
            )
        }

        if (mode == LockScreenMode.UNLOCK && biometricAvailableAndEnabled) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRequestBiometric) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use biometric unlock")
            }
        }
    }
}
