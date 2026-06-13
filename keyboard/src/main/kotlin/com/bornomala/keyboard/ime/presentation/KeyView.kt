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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.bornomala.keyboard.ime.domain.model.KeyIcon
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
    onLongPressRequested: (Key, LayoutCoordinates) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressReleased: () -> Unit,
    modifier: Modifier = Modifier,
    isPopupSource: Boolean = false,
    flat: Boolean = false,
    repeatIntervalMs: Long = 45L,
    longPressTimeoutMs: Long = 300L,
) {
    val colors = BornomalaTheme.keyboardColors
    var pressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val style = key.style
    val isAccentEnter = style == KeyStyle.FUNCTIONAL && key.action == KeyAction.Enter && enterIsAccent
    val effectiveStyle = if (isAccentEnter) KeyStyle.ACCENT else style

    // While this key is the active long-press source it takes the theme's accent color.
    val background = when {
        isPopupSource -> colors.accentKeyBackground
        // Flat strip keys: no background (transparent), faint highlight only while pressed.
        flat -> if (pressed) colors.keyBackgroundPressed else Color.Transparent
        else -> when (effectiveStyle) {
            KeyStyle.NORMAL -> if (pressed) colors.keyBackgroundPressed else colors.keyBackground
            KeyStyle.FUNCTIONAL -> if (pressed) colors.functionalKeyBackgroundPressed else colors.functionalKeyBackground
            KeyStyle.SPACEBAR -> if (pressed) colors.keyBackgroundPressed else colors.spacebarBackground
            KeyStyle.ACCENT -> colors.accentKeyBackground
        }
    }
    val contentColor = when {
        isPopupSource -> colors.accentKeyContent
        flat -> colors.functionalKeyContent
        else -> when (effectiveStyle) {
            KeyStyle.NORMAL -> colors.keyContent
            KeyStyle.FUNCTIONAL -> colors.functionalKeyContent
            KeyStyle.SPACEBAR -> colors.spacebarContent
            KeyStyle.ACCENT -> colors.accentKeyContent
        }
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
            .onGloballyPositioned { coordinates = it }
            .padding(horizontal = gap / 2, vertical = vGap / 2)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .then(
                if (metrics.keyBorder) {
                    // Derive the stroke from the key's own content colour so it stays visible on
                    // every theme (a light outline on dark keys, a dark outline on light keys),
                    // instead of a single faint tint that vanishes on dark backgrounds.
                    Modifier.border(
                        1.5.dp,
                        contentColor.copy(alpha = 0.30f),
                        RoundedCornerShape(cornerRadius),
                    )
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
                        val coords = coordinates
                        if (up == null && coords != null) {
                            // Held long enough: show the popup above this key, then keep tracking
                            // the finger so a swipe (while still held) moves the highlight; the
                            // selected glyph is committed on release.
                            onLongPressRequested(key, coords)
                            while (true) {
                                val moveEvent = awaitPointerEvent()
                                val change = moveEvent.changes.firstOrNull() ?: break
                                onLongPressMove(coords.localToWindow(change.position))
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                            }
                            onLongPressReleased()
                        } else {
                            onKey(key.action)
                        }
                    } else if (key.longPressKeyAction != null) {
                        // Discrete long-press: hold fires the alternate action (e.g. the language
                        // key opens the IME picker); a short tap fires the normal action.
                        val up = waitForUpOrCancellationWindowed(longPressTimeoutMs)
                        if (up == null) {
                            onKey(key.longPressKeyAction)
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
        KeyContent(key = key, shift = shift, contentColor = contentColor, flat = flat)
    }
}

@Composable
private fun KeyContent(key: Key, shift: ShiftState, contentColor: Color, flat: Boolean = false) {
    val labelScale = BornomalaTheme.metrics.keyLabelScale
    val icon = iconFor(key, shift)
    if (icon != null) {
        // All icon keys use a single 20dp base, scaled by the "Key label size" slider (like the
        // text labels) so they track it predictably and don't drift with key height/gaps.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp * labelScale),
            )
        }
        return
    }

    val label = labelFor(key, shift)
    val isSpacebar = key.style == KeyStyle.SPACEBAR
    // Multi-char function labels (?123, ABC, =\<) read better a touch smaller than glyphs.
    val labelSize = when {
        flat -> 16.sp
        isSpacebar -> 15.4.sp
        label.length > 1 -> 15.5.sp
        else -> 22.sp
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

private fun iconFor(key: Key, shift: ShiftState): ImageVector? {
    // An explicit per-key override wins over the action-derived icon, so two keys sharing one
    // action can render different glyphs (e.g. the numpad space key vs. the main spacebar).
    key.iconOverride?.let { return when (it) {
        KeyIcon.SPACE -> LucideIcons.Space
    } }
    return when (key.action) {
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
    KeyAction.ShowImePicker -> "Switch keyboard"
    is KeyAction.Text -> (key.action as KeyAction.Text).text
    KeyAction.None -> ""
}
