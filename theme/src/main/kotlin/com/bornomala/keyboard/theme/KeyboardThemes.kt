package com.bornomala.keyboard.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Selectable keyboard color themes (Gboard-style). [SYSTEM]/[LIGHT]/[DARK] reuse the base
 * neutral palettes; the rest are curated colored (dark) palettes. The chosen theme drives
 * the keyboard's [KeyboardColors] and the Material scheme's light/dark-ness.
 */
enum class KeyboardTheme(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    MIDNIGHT("Midnight"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    GRAPE("Grape"),
    ROSE("Rose"),
    SOLARIZED("Solarized"),
    ;

    companion object {
        /** Tolerant parse of a persisted name; unknown values fall back to [SYSTEM]. */
        fun fromName(raw: String?): KeyboardTheme = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/** Whether this theme renders dark (drives the Material scheme used for chrome/dialogs). */
fun KeyboardTheme.isDark(systemInDark: Boolean): Boolean = when (this) {
    KeyboardTheme.LIGHT -> false
    KeyboardTheme.SYSTEM -> systemInDark
    else -> true
}

/** Resolves the [KeyboardColors] for a theme. Public so the settings picker can preview swatches. */
fun keyboardColorsFor(theme: KeyboardTheme, systemInDark: Boolean): KeyboardColors = when (theme) {
    KeyboardTheme.SYSTEM -> if (systemInDark) DarkKeyboardColors else LightKeyboardColors
    KeyboardTheme.LIGHT -> LightKeyboardColors
    KeyboardTheme.DARK -> DarkKeyboardColors
    KeyboardTheme.MIDNIGHT -> colored(0xFF0B0D12, 0xFF1A1E27, 0xFF252B36, 0xFF13161D, 0xFF222732, 0xFF4C8DF6)
    KeyboardTheme.OCEAN -> colored(0xFF0C2733, 0xFF134155, 0xFF1B5168, 0xFF0F3343, 0xFF1B5168, 0xFF2BC0D6)
    KeyboardTheme.FOREST -> colored(0xFF0E2018, 0xFF163026, 0xFF1E4033, 0xFF112A20, 0xFF1E4033, 0xFF49C07A)
    KeyboardTheme.SUNSET -> colored(0xFF2A1512, 0xFF3C211B, 0xFF4D2C23, 0xFF331A15, 0xFF4D2C23, 0xFFF0743A)
    KeyboardTheme.GRAPE -> colored(0xFF1C1430, 0xFF2A1E45, 0xFF362858, 0xFF221839, 0xFF362858, 0xFFA277F0)
    KeyboardTheme.ROSE -> colored(0xFF2A1320, 0xFF3C1D2C, 0xFF4D2738, 0xFF331826, 0xFF4D2738, 0xFFF06B9E)
    // Slate tray + lighter slate digit keys, darker slate functional/rail keys (shift, ?123,
    // comma, period, globe, delete, numpad rails), teal accent (Enter) — so the grid reads with
    // the same digit/functional contrast as the other themes.
    KeyboardTheme.SOLARIZED -> colored(0xFF212B30, 0xFF394147, 0xFF454D53, 0xFF2D353A, 0xFF394147, 0xFF39A097)
}

/** Builds a full [KeyboardColors] from a few seed colors (all colored themes are dark). */
private fun colored(
    tray: Long,
    key: Long,
    keyPressed: Long,
    functional: Long,
    functionalPressed: Long,
    accent: Long,
): KeyboardColors {
    val content = Color(0xFFEDEFF5)
    val accentColor = Color(accent)
    return KeyboardColors(
        keyboardBackground = Color(tray),
        keyBackground = Color(key),
        keyBackgroundPressed = Color(keyPressed),
        keyContent = content,
        functionalKeyBackground = Color(functional),
        functionalKeyBackgroundPressed = Color(functionalPressed),
        functionalKeyContent = content,
        spacebarBackground = Color(key),
        spacebarContent = content.copy(alpha = 0.7f),
        accentKeyBackground = accentColor,
        accentKeyContent = Color.White,
        suggestionBarBackground = Color(tray),
        suggestionText = content,
        suggestionTextHighlighted = accentColor,
        suggestionDivider = Color(keyPressed),
        // Lighter than the keys so the long-press popup card reads as raised, not blended.
        popupBackground = Color(keyPressed),
        popupContent = content,
        keyStroke = Color(0x22000000),
    )
}

/**
 * Theme variant of [BornomalaTheme] driven by a selected [KeyboardTheme] (instead of just
 * light/dark). Used by the keyboard and the settings screen so both reflect the chosen theme.
 */
@Composable
fun BornomalaTheme(
    theme: KeyboardTheme,
    font: KeyboardFont = KeyboardFont.SYSTEM,
    metrics: KeyboardMetrics = keyboardMetrics(),
    // Material You (dynamic wallpaper-derived colors) for the app's Activity surfaces only.
    // It recolors the MaterialTheme scheme (settings/onboarding UI), never the keyboard, which
    // always renders from the fixed [keyboardColors] below to stay consistent across host apps.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = theme.isDark(systemDark)
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (dark) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        dark -> BornomalaDarkColorScheme
        else -> BornomalaLightColorScheme
    }
    val keyboardColors = keyboardColorsFor(theme, systemDark)

    CompositionLocalProvider(
        LocalKeyboardColors provides keyboardColors,
        LocalKeyboardFontFamily provides keyboardFontFamily(font),
        LocalKeyboardMetrics provides metrics,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = BornomalaShapes,
            typography = BornomalaTypography.withFontFamily(keyboardFontFamily(font)),
            content = content,
        )
    }
}
