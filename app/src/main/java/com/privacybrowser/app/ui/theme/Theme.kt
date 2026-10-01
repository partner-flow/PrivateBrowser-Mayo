package com.privacybrowser.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E1FF),
    onSecondaryContainer = Color(0xFF2B1F5C),
    background = LightBackground,
    onBackground = Color(0xFF1B1C1F),
    surface = LightSurface,
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF45474E),
    outline = LightOutline,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color(0xFF00284D),
    secondary = AccentPurpleDark,
    onSecondary = Color(0xFF2B1F5C),
    secondaryContainer = Color(0xFF362B6E),
    onSecondaryContainer = Color(0xFFE9E1FF),
    background = DarkBackground,
    onBackground = Color(0xFFE4E5E8),
    surface = DarkSurface,
    onSurface = Color(0xFFE4E5E8),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC3C5CC),
    outline = DarkOutline,
    error = ErrorRedDark
)

/**
 * @param forceDark null = follow system, true/false = explicit override from Settings.
 *
 * Deliberately does NOT use Android 12+ dynamic/wallpaper-based color. A "premium" theme means
 * every install looks the same considered, designed palette — not whatever happens to be
 * extracted from the user's wallpaper that day.
 */
@Composable
fun PrivateBrowserTheme(
    forceDark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = forceDark ?: systemDark
    val colorScheme = if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PrivateBrowserShapes,
        content = content
    )
}
