package com.privacybrowser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One set of corner radii reused everywhere (buttons, cards, dialogs, the address bar, sheets),
 * so rounding is consistent across the whole app instead of each screen inventing its own.
 */
val PrivateBrowserShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
