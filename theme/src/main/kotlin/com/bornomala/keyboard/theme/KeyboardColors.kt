package com.bornomala.keyboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Keyboard-specific color tokens that go beyond the Material 3 [androidx.compose.material3.ColorScheme].
 *
 * The keyboard distinguishes several key roles (normal letter keys, functional keys
 * like shift/backspace, the spacebar, the accent action key) and needs explicit
 * pressed-state colors for sub-16ms visual feedback. These are exposed through
 * [LocalKeyboardColors] so the `:keyboard` module's renderer can read them without a
 * hard dependency on which theme variant is active.
 */
@Immutable
data class KeyboardColors(
    /** Backdrop behind all keys (the keyboard "tray"). */
    val keyboardBackground: Color,
    /** Default letter/number key face. */
    val keyBackground: Color,
    /** Letter/number key face while pressed. */
    val keyBackgroundPressed: Color,
    /** Glyph color on default keys. */
    val keyContent: Color,
    /** Functional keys: shift, backspace, symbols, language switch, enter. */
    val functionalKeyBackground: Color,
    val functionalKeyBackgroundPressed: Color,
    val functionalKeyContent: Color,
    /** The spacebar face. */
    val spacebarBackground: Color,
    val spacebarContent: Color,
    /** Accent action key (e.g. active enter / send). */
    val accentKeyBackground: Color,
    val accentKeyContent: Color,
    /** Suggestion strip background and text. */
    val suggestionBarBackground: Color,
    val suggestionText: Color,
    val suggestionTextHighlighted: Color,
    val suggestionDivider: Color,
    /** Popup shown on long-press / key-preview. */
    val popupBackground: Color,
    val popupContent: Color,
    /** Hairline borders / shadows under keys. */
    val keyStroke: Color,
)

internal val LightKeyboardColors = KeyboardColors(
    keyboardBackground = BornomalaPalette.Grey95,
    keyBackground = BornomalaPalette.White,
    keyBackgroundPressed = BornomalaPalette.Grey90,
    keyContent = BornomalaPalette.OnLight,
    functionalKeyBackground = BornomalaPalette.Grey90,
    functionalKeyBackgroundPressed = BornomalaPalette.Grey85,
    functionalKeyContent = BornomalaPalette.OnLightVariant,
    spacebarBackground = BornomalaPalette.White,
    spacebarContent = BornomalaPalette.OnLightVariant,
    accentKeyBackground = BornomalaPalette.Blue40,
    accentKeyContent = BornomalaPalette.White,
    suggestionBarBackground = BornomalaPalette.Grey95,
    suggestionText = BornomalaPalette.OnLight,
    suggestionTextHighlighted = BornomalaPalette.Blue40,
    suggestionDivider = BornomalaPalette.Grey85,
    popupBackground = BornomalaPalette.White,
    popupContent = BornomalaPalette.OnLight,
    keyStroke = BornomalaPalette.Grey85,
)

internal val DarkKeyboardColors = KeyboardColors(
    keyboardBackground = BornomalaPalette.Grey10,
    keyBackground = BornomalaPalette.Grey25,
    keyBackgroundPressed = BornomalaPalette.Grey30,
    keyContent = BornomalaPalette.OnDark,
    functionalKeyBackground = BornomalaPalette.Grey20,
    functionalKeyBackgroundPressed = BornomalaPalette.Grey30,
    functionalKeyContent = BornomalaPalette.OnDarkVariant,
    spacebarBackground = BornomalaPalette.Grey25,
    spacebarContent = BornomalaPalette.OnDarkVariant,
    accentKeyBackground = BornomalaPalette.Blue50,
    accentKeyContent = BornomalaPalette.White,
    suggestionBarBackground = BornomalaPalette.Grey10,
    suggestionText = BornomalaPalette.OnDark,
    suggestionTextHighlighted = BornomalaPalette.Blue80,
    suggestionDivider = BornomalaPalette.Grey30,
    popupBackground = BornomalaPalette.Grey25,
    popupContent = BornomalaPalette.OnDark,
    keyStroke = BornomalaPalette.Grey15,
)

/**
 * Provides [KeyboardColors] down the composition. Defaults to light; the real value
 * is supplied by [BornomalaTheme]. Accessing it outside the theme yields light tokens
 * rather than throwing, so isolated previews still render.
 */
val LocalKeyboardColors = staticCompositionLocalOf { LightKeyboardColors }
