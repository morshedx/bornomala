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
 */
@Composable
fun BornomalaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Material You for app Activity surfaces only; the keyboard keeps its fixed palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (useDark) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        useDark -> BornomalaDarkColorScheme
        else -> BornomalaLightColorScheme
    }
    val keyboardColors = if (useDark) DarkKeyboardColors else LightKeyboardColors

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

    /** Key-label font family for the active theme scope; null = system default. */
    val keyFontFamily: androidx.compose.ui.text.font.FontFamily?
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardFontFamily.current

    /** User-tunable layout metrics (gaps, label/bar scale, border) for the active scope. */
    val metrics: KeyboardMetrics
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardMetrics.current
}
