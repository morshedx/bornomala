package com.bornomala.keyboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keyboard dimension tokens shared between the renderer and settings (height
 * adjustment). Values are baseline defaults; the user's preferred height multiplier
 * from settings scales [keyRowHeight] at runtime.
 *
 * Touch targets meet the 48dp accessibility minimum for the effective hit area even
 * when the visible key is shorter, via padding in the renderer.
 */
@Immutable
object KeyboardDimens {

    /** Default height of a single key row before user scaling. */
    val keyRowHeight: Dp = 52.dp

    /** Min / max bounds for the user-adjustable row height (settings slider). */
    val minKeyRowHeight: Dp = 40.dp
    val maxKeyRowHeight: Dp = 72.dp

    /** Height of the suggestion strip above the keys. */
    val suggestionBarHeight: Dp = 44.dp

    /** Height of the emoji / clipboard category tab strip. */
    val panelTabStripHeight: Dp = 44.dp

    /** Gap between adjacent keys (applied as half on each side). */
    val keyHorizontalGap: Dp = 4.dp
    val keyVerticalGap: Dp = 8.dp

    /** Outer padding around the whole keyboard tray. */
    val keyboardHorizontalPadding: Dp = 4.dp
    val keyboardVerticalPadding: Dp = 6.dp

    /** Minimum interactive size for accessibility (TalkBack / large targets). */
    val minTouchTarget: Dp = 48.dp

    /** Long-press popup sizing. */
    val popupKeyWidth: Dp = 40.dp
    val popupKeyHeight: Dp = 48.dp

    /** Key glyph text sizes (sp expressed via theme typography; these are dp guides). */
    val keyLabelSize: Dp = 22.dp
    val keyHintLabelSize: Dp = 11.dp
}
