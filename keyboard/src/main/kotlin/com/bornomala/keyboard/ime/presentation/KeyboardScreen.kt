package com.bornomala.keyboard.ime.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.bornomala.keyboard.ime.data.layout.LayoutProvider
import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyboardPage
import com.bornomala.keyboard.ime.domain.model.KeyboardPanel
import com.bornomala.keyboard.ime.domain.model.KeyboardState
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * The complete keyboard surface: suggestion bar, the active key grid, and the long-press
 * popup overlay. Rendered inside [com.bornomala.keyboard.theme.BornomalaTheme] by the IME
 * service.
 *
 * Performance:
 *  - The active [com.bornomala.keyboard.ime.domain.model.KeyboardLayout] is resolved from
 *    the [layoutProvider] in a keyed [remember] so it is a pure cache lookup that only
 *    re-runs when language/page/number-row change — never per keystroke.
 *  - [callbacks] is an `@Immutable` holder created once by the host, so key composables stay
 *    stable across recompositions (no recomposition storms on the sub-16ms key path).
 *  - The row height is derived from the user's height preference and applied per row.
 *
 * @param state immutable keyboard state to render.
 * @param layoutProvider pre-built layout cache (injected singleton).
 * @param callbacks stable input callbacks.
 * @param keyHeightFraction 0..1 position between the min and max configurable row heights.
 */
@Composable
internal fun KeyboardScreen(
    state: KeyboardState,
    layoutProvider: LayoutProvider,
    callbacks: KeyboardCallbacks,
    keyHeightFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = BornomalaTheme.keyboardColors
    val dimens = BornomalaTheme.dimens

    val layout = remember(state.language, state.page, state.showNumberRow, state.isEmailField) {
        layoutProvider.layoutFor(state.language, state.page, state.showNumberRow, state.isEmailField)
    }
    val rowHeight = remember(keyHeightFraction) {
        lerpDp(dimens.minKeyRowHeight, dimens.maxKeyRowHeight, keyHeightFraction.coerceIn(0f, 1f))
    }

    var popupKey by remember { mutableStateOf<Key?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            // Reserve the gesture/navigation-bar inset: the tray background paints to the
            // bottom edge while the keys sit above the gesture pill (no black gap, no overlap).
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimens.keyboardHorizontalPadding,
                    end = dimens.keyboardHorizontalPadding,
                    top = dimens.keyboardVerticalPadding,
                    // Extra bottom margin (above the gesture inset) so the last row clears the
                    // gesture handle comfortably, matching Gboard's spacing.
                    bottom = dimens.keyboardVerticalPadding + 18.dp,
                ),
        ) {
            ActionStrip(
                suggestions = if (state.suggestionsEnabled) state.suggestions else emptyList(),
                emojiActive = state.panel == KeyboardPanel.EMOJI,
                clipboardActive = state.panel == KeyboardPanel.CLIPBOARD,
                numpadActive = state.page == KeyboardPage.NUMPAD,
                callbacks = callbacks,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp * BornomalaTheme.metrics.suggestionBarScale),
            )

            // Panels are sized to the alphabetic keyboard's key area so the IME window stays
            // keyboard-height instead of expanding to fill the screen.
            val panelHeight = remember(state.language, state.showNumberRow, rowHeight) {
                val rows = layoutProvider
                    .layoutFor(state.language, KeyboardPage.ALPHA, state.showNumberRow)
                    .rows.size
                rowHeight * rows
            }

            if (state.panel == KeyboardPanel.EMOJI) {
                EmojiHost(
                    onEmoji = callbacks.onEmoji,
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                )
            } else if (state.panel == KeyboardPanel.CLIPBOARD) {
                ClipboardHost(
                    onPaste = callbacks.onPaste,
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                )
            } else {
                layout.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                    ) {
                        row.keys.forEach { key ->
                            KeyView(
                                key = key,
                                shift = state.shift,
                                enterIsAccent = state.enterIsAccent,
                                onKey = callbacks.onKey,
                                onLongPressChar = callbacks.onLongPressChar,
                                onLongPressRequested = { pressedKey ->
                                    // Long-pressing the comma opens keyboard settings
                                    // (Gboard-style) instead of showing a character popup.
                                    if (pressedKey.action == KeyAction.Character(',')) {
                                        callbacks.onOpenSettings()
                                    } else {
                                        popupKey = pressedKey
                                    }
                                },
                                onLongPressDismissed = { /* popup stays interactive until a pick or scrim tap */ },
                                modifier = Modifier
                                    .weight(key.weight)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        val activePopup = popupKey
        if (activePopup != null) {
            LongPressPopup(
                key = activePopup,
                onPick = {
                    callbacks.onLongPressChar(it)
                    popupKey = null
                },
                onDismiss = { popupKey = null },
            )
        }
    }
}

/**
 * A simple overlay listing the long-press alternatives for [key]. A full-surface scrim
 * dismisses it; tapping a glyph commits it. Kept lightweight (no animation) and centred at
 * the top of the keyboard so it never falls outside the IME window.
 */
@Composable
private fun LongPressPopup(
    key: Key,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BornomalaTheme.keyboardColors
    val chars = remember(key) { popupChars(key) }
    if (chars.isEmpty()) {
        onDismiss()
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.popupBackground)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chars.forEach { c ->
                Text(
                    text = c.toString(),
                    color = colors.popupContent,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .size(width = 40.dp, height = 44.dp)
                        .clickable { onPick(c) }
                        .padding(top = 10.dp),
                )
            }
        }
    }
}

private fun popupChars(key: Key): List<Char> {
    val primary = (key.action as? KeyAction.Character)?.char
    if (key.longPressChars.isEmpty()) return primary?.let { listOf(it) } ?: emptyList()
    val result = ArrayList<Char>(key.longPressChars.size + 1)
    if (primary != null) result.add(primary)
    for (c in key.longPressChars) if (c != primary) result.add(c)
    return result
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction
