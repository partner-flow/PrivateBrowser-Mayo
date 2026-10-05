package com.privacybrowser.app.ui.theme

import androidx.compose.ui.graphics.Color

// Premium palette: a single blue/purple accent pair, reused consistently across both themes and
// every screen (browser, tabs, history, bookmarks, settings, dialogs, buttons, cards) so nothing
// looks like it belongs to a different app. No dynamic/wallpaper-based color is used (see
// Theme.kt) specifically so this palette is what people actually see.
val AccentBlue = Color(0xFF3B7DD8)
val AccentBlueDark = Color(0xFF6FA8FF)
val AccentPurple = Color(0xFF7B5AF0)       // used for the Private Mode accent
val AccentPurpleDark = Color(0xFFB49CFF)

// Light theme: clean white/light-grey, good contrast, no tint bleeding into every surface.
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDEFF4)
val LightOutline = Color(0xFFC6C9D2)

// Dark theme: premium charcoal/near-black, not a flat pure black (which flattens depth cues and
// reads as cheap) and not a glowing/neon dark theme — restrained accent use only.
val DarkBackground = Color(0xFF111318)
val DarkSurface = Color(0xFF1A1D23)
val DarkSurfaceVariant = Color(0xFF24272F)
val DarkOutline = Color(0xFF3A3E47)

val ErrorRed = Color(0xFFD64545)
val ErrorRedDark = Color(0xFFFF8A80)
