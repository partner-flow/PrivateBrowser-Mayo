package com.privacybrowser.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrivacyBlue,
    secondary = PrivateModePurple,
    background = LightBackground,
    surface = LightSurface,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = PrivacyBlueDark,
    secondary = PrivateModePurpleDark,
    background = DarkBackground,
    surface = DarkSurface,
    error = ErrorRed
)

/**
 * @param forceDark null = follow system, true/false = explicit override from Settings.
 */
@Composable
fun PrivateBrowserTheme(
    forceDark: Boolean? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = forceDark ?: systemDark
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
