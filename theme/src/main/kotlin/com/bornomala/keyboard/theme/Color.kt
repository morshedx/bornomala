package com.bornomala.keyboard.theme

import androidx.compose.ui.graphics.Color

/**
 * Samsung-inspired color tokens.
 *
 * The Samsung Keyboard visual language favors a near-white/near-black neutral
 * backdrop, soft elevated keys, and a single confident blue accent. These raw
 * tokens feed both the Material 3 [androidx.compose.material3.ColorScheme]s and the
 * keyboard-specific [KeyboardColors] surface used by the key renderer.
 */
internal object BornomalaPalette {

    // Accent — Samsung "One UI" blue family.
    val Blue40 = Color(0xFF1A73E8)
    val Blue50 = Color(0xFF2D7DF6)
    val Blue80 = Color(0xFFAEC9FF)
    val Blue90 = Color(0xFFD7E6FF)

    // Light neutrals.
    val Grey99 = Color(0xFFFBFBFE)
    val Grey95 = Color(0xFFEEF1F6)
    val Grey90 = Color(0xFFE2E6EC)
    val Grey85 = Color(0xFFD4D9E1)
    val White = Color(0xFFFFFFFF)

    // Dark neutrals.
    val Grey10 = Color(0xFF111317)
    val Grey15 = Color(0xFF1A1D22)
    val Grey20 = Color(0xFF22262C)
    val Grey25 = Color(0xFF2C3138)
    val Grey30 = Color(0xFF383E47)

    // Text.
    val OnLight = Color(0xFF1A1C1E)
    val OnLightVariant = Color(0xFF44474C)
    val OnDark = Color(0xFFE3E2E6)
    val OnDarkVariant = Color(0xFFC4C6CF)

    // Semantic.
    val Error = Color(0xFFBA1A1A)
    val ErrorDark = Color(0xFFFFB4AB)
}
