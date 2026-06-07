package com.bornomala.keyboard.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Selectable key-label fonts. [SYSTEM] uses the platform default; more can be bundled later.
 */
enum class KeyboardFont(val displayName: String) {
    SYSTEM("System default"),
    JETBRAINS_MONO("JetBrains Mono"),
    ;

    companion object {
        fun fromName(raw: String?): KeyboardFont = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

internal val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** The [FontFamily] for a chosen font, or null for the system default. */
fun keyboardFontFamily(font: KeyboardFont): FontFamily? = when (font) {
    KeyboardFont.SYSTEM -> null
    KeyboardFont.JETBRAINS_MONO -> JetBrainsMonoFamily
}

/** Key-label font family for the current theme scope; null = system default. */
val LocalKeyboardFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/** Returns a copy of this [Typography] with every text style using [family] (no-op if null). */
fun Typography.withFontFamily(family: FontFamily?): Typography {
    if (family == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}
