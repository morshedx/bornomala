package com.bornomala.keyboard.ime.presentation

import com.bornomala.keyboard.theme.LucideIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyStyle
import com.bornomala.keyboard.ime.domain.model.ShiftState
import com.bornomala.keyboard.theme.BornomalaTheme

/**
 * Renders a single key. Performance notes:
 *  - Pressed state is local [mutableStateOf] so only this key recomposes on press, not the
 *    whole keyboard. No animation runs on the press path (cheap color swap only).
 *  - Long-press and key-repeat are handled with a low-level pointer gesture instead of the
 *    higher-level clickable to avoid ripple animation cost and to support hold-to-repeat.
 *  - Color/role resolution reads the immutable theme tokens; no allocation per press.
 *
 * @param key the key data.
 * @param shift current shift state (drives uppercase glyph + shift icon state).
 * @param repeatIntervalMs interval between repeats while a repeatable key is held.
 */
@Composable
internal fun KeyView(
    key: Key,
    shift: ShiftState,
    enterIsAccent: Boolean,
    onKey: (KeyAction) -> Unit,
    onLongPressChar: (Char) -> Unit,
    onLongPressRequested: (Key) -> Unit,
    onLongPressDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    repeatIntervalMs: Long = 45L,
    longPressTimeoutMs: Long = 300L,
) {
    val colors = BornomalaTheme.keyboardColors
    var pressed by remember { mutableStateOf(false) }

    val style = key.style
    val isAccentEnter = style == KeyStyle.FUNCTIONAL && key.action == KeyAction.Enter && enterIsAccent
    val effectiveStyle = if (isAccentEnter) KeyStyle.ACCENT else style

    val background = when (effectiveStyle) {
        KeyStyle.NORMAL -> if (pressed) colors.keyBackgroundPressed else colors.keyBackground
        KeyStyle.FUNCTIONAL -> if (pressed) colors.functionalKeyBackgroundPressed else colors.functionalKeyBackground
        KeyStyle.SPACEBAR -> if (pressed) colors.keyBackgroundPressed else colors.spacebarBackground
        KeyStyle.ACCENT -> colors.accentKeyBackground
    }
    val contentColor = when (effectiveStyle) {
        KeyStyle.NORMAL -> colors.keyContent
        KeyStyle.FUNCTIONAL -> colors.functionalKeyContent
        KeyStyle.SPACEBAR -> colors.spacebarContent
        KeyStyle.ACCENT -> colors.accentKeyContent
    }

    val cornerRadius = when (effectiveStyle) {
        KeyStyle.SPACEBAR -> BornomalaTheme.shapes.spacebarCornerRadius
        else -> BornomalaTheme.shapes.keyCornerRadius
    }

    val metrics = BornomalaTheme.metrics
    val gap = metrics.horizontalGap
    val vGap = metrics.verticalGap
    val description = key.contentDescription ?: defaultDescription(key, shift)

    Box(
        modifier = modifier
            .padding(horizontal = gap / 2, vertical = vGap / 2)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .then(
                if (metrics.keyBorder) {
                    Modifier.border(1.dp, colors.keyStroke, RoundedCornerShape(cornerRadius))
                } else {
                    Modifier
                },
            )
            .semantics {
                this.contentDescription = description
                this.role = Role.Button
            }
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true

                    if (key.repeatable) {
                        // Initial action immediately, then a short pause before repeating,
                        // then repeat at the fast interval while still held.
                        onKey(key.action)
                        val initialDelay = 350L
                        var up = waitForUpOrCancellationWindowed(initialDelay)
                        // Cancellation (e.g. leaving composition) propagates out of
                        // awaitPointerEvent, so `pressed` + the up-check bound the loop.
                        while (up == null && pressed) {
                            onKey(key.action)
                            up = waitForUpOrCancellationWindowed(repeatIntervalMs)
                        }
                    } else if (key.longPressChars.isNotEmpty()) {
                        val up = waitForUpOrCancellationWindowed(longPressTimeoutMs)
                        if (up == null) {
                            // Held long enough: show popup; selection handled by overlay.
                            onLongPressRequested(key)
                            // Wait for the actual release to dismiss; the overlay reports the char.
                            waitForUpOrCancellation()
                            onLongPressDismissed()
                        } else {
                            onKey(key.action)
                        }
                    } else {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            onKey(key.action)
                        }
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        KeyContent(key = key, shift = shift, contentColor = contentColor)
    }
}

@Composable
private fun KeyContent(key: Key, shift: ShiftState, contentColor: Color) {
    val icon = iconFor(key, shift)
    if (icon != null) {
        // The language-switch (globe) key reads better a bit larger than the other glyphs,
        // so it gets less inset.
        val iconPadding = if (key.action == KeyAction.SwitchLanguage) 7.dp else 12.dp
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.fillMaxSize().padding(iconPadding),
        )
        return
    }

    val label = labelFor(key, shift)
    val isSpacebar = key.style == KeyStyle.SPACEBAR
    val labelScale = BornomalaTheme.metrics.keyLabelScale
    // Multi-char function labels (?123, ABC, =\<) read better a touch smaller than glyphs.
    val labelSize = when {
        isSpacebar -> 14.sp
        label.length > 1 -> 15.sp
        else -> 20.sp
    } * labelScale
    val fontFamily = BornomalaTheme.keyFontFamily
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = contentColor,
            fontSize = labelSize,
            fontFamily = fontFamily,
            fontWeight = if (isSpacebar) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        val hint = key.hint
        if (hint != null) {
            Text(
                text = hint,
                color = contentColor.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = fontFamily,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 6.dp),
            )
        }
    }
}

private fun labelFor(key: Key, shift: ShiftState): String {
    if (key.label.isEmpty()) return ""
    if (key.action is KeyAction.Character) {
        val c = key.action.char
        if (c.isLetter()) {
            return if (shift.isUpper) c.uppercaseChar().toString() else c.lowercaseChar().toString()
        }
    }
    return key.shiftedLabel?.takeIf { shift.isUpper } ?: key.label
}

private fun iconFor(key: Key, shift: ShiftState): ImageVector? = when (key.action) {
    KeyAction.Backspace -> LucideIcons.Delete
    KeyAction.Enter -> LucideIcons.CornerDownLeft
    KeyAction.SwitchLanguage -> LucideIcons.Globe
    KeyAction.Emoji -> LucideIcons.Smile
    KeyAction.Shift -> when (shift) {
        ShiftState.CAPS_LOCK -> LucideIcons.ArrowBigUpDash
        else -> LucideIcons.ArrowBigUp
    }
    else -> null
}

private fun defaultDescription(key: Key, shift: ShiftState): String = when (key.action) {
    is KeyAction.Character -> {
        val c = (key.action as KeyAction.Character).char
        if (c.isLetter()) (if (shift.isUpper) c.uppercaseChar() else c).toString() else c.toString()
    }
    KeyAction.Backspace -> "Delete"
    KeyAction.Enter -> "Enter"
    KeyAction.Space -> "Space"
    KeyAction.Shift -> when (shift) {
        ShiftState.OFF -> "Shift"
        ShiftState.SHIFTED -> "Shift enabled"
        ShiftState.CAPS_LOCK -> "Caps lock"
    }
    KeyAction.SwitchLanguage -> "Switch language"
    KeyAction.ToSymbols -> "Symbols"
    KeyAction.ToAlpha -> "Letters"
    KeyAction.ToggleSymbolsPage -> "More symbols"
    KeyAction.Emoji -> "Emoji"
    is KeyAction.Text -> (key.action as KeyAction.Text).text
    KeyAction.None -> ""
}
