package com.bornomala.keyboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * User-tunable keyboard layout metrics (the "Key gaps & sizes" configurator). Provided down
 * the composition by [BornomalaTheme] so the renderer reads live values without threading
 * them through every composable.
 *
 * @param horizontalGap gap between adjacent keys in a row.
 * @param verticalGap gap between key rows.
 * @param keyLabelScale multiplier on key glyph/label text size.
 * @param suggestionBarScale multiplier on the action/suggestion strip height.
 * @param keyBorder whether keys draw a hairline border (vs. flush fill).
 */
@Immutable
data class KeyboardMetrics(
    val horizontalGap: Dp,
    val verticalGap: Dp,
    val keyLabelScale: Float,
    val suggestionBarScale: Float,
    val keyBorder: Boolean,
)

/** Builds metrics by scaling the baseline [KeyboardDimens] tokens. */
fun keyboardMetrics(
    horizontalGapScale: Float = 1f,
    verticalGapScale: Float = 1f,
    keyLabelScale: Float = 1f,
    suggestionBarScale: Float = 1f,
    keyBorder: Boolean = false,
): KeyboardMetrics = KeyboardMetrics(
    horizontalGap = KeyboardDimens.keyHorizontalGap * horizontalGapScale.coerceIn(0.25f, 2f),
    verticalGap = KeyboardDimens.keyVerticalGap * verticalGapScale.coerceIn(0.25f, 2f),
    keyLabelScale = keyLabelScale.coerceIn(0.7f, 1.5f),
    suggestionBarScale = suggestionBarScale.coerceIn(0.7f, 1.5f),
    keyBorder = keyBorder,
)

val LocalKeyboardMetrics = staticCompositionLocalOf { keyboardMetrics() }
