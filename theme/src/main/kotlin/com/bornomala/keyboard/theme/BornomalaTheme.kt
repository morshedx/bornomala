package com.bornomala.keyboard.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Root theme for all Bornomala Compose surfaces (settings screens, dialogs, and the
 * keyboard view). Resolves [ThemeMode] to a concrete light/dark Material 3 scheme and
 * additionally provides keyboard-specific [KeyboardColors] via [LocalKeyboardColors].
 *
 * Dynamic color is intentionally not used: the keyboard must look consistent across
 * the many host apps it appears in, matching Samsung Keyboard's stable identity.
 *
 * @param themeMode user preference; defaults to [ThemeMode.SYSTEM].
 * @param highContrast reserved for the accessibility high-contrast option; when true
 *   the keyboard stroke tokens are emphasized for clearer key separation.
 */
@Composable
fun BornomalaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) BornomalaDarkColorScheme else BornomalaLightColorScheme
    val baseKeyboardColors = if (useDark) DarkKeyboardColors else LightKeyboardColors
    val keyboardColors =
        if (highContrast) baseKeyboardColors.toHighContrast(useDark) else baseKeyboardColors

    CompositionLocalProvider(LocalKeyboardColors provides keyboardColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = BornomalaShapes,
            typography = BornomalaTypography,
            content = content,
        )
    }
}

/**
 * Convenience accessors mirroring [MaterialTheme] for the bespoke keyboard tokens.
 * Lets the renderer read `BornomalaTheme.keyboardColors` alongside `MaterialTheme.*`.
 */
object BornomalaTheme {
    val keyboardColors: KeyboardColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardColors.current

    val shapes: KeyboardShapeTokens
        get() = KeyboardShapeTokens

    val dimens: KeyboardDimens
        get() = KeyboardDimens
}

/**
 * Strengthens key separation for the high-contrast accessibility mode by darkening /
 * lightening the stroke and dividers relative to the base scheme.
 */
private fun KeyboardColors.toHighContrast(useDark: Boolean): KeyboardColors {
    val emphasizedStroke = if (useDark) BornomalaPalette.OnDarkVariant else BornomalaPalette.OnLight
    return copy(
        keyStroke = emphasizedStroke,
        suggestionDivider = emphasizedStroke,
    )
}
