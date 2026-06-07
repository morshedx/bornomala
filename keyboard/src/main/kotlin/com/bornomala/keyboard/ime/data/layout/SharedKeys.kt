package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyStyle

/**
 * Reusable functional keys and rows shared across every layout. Declared as top-level
 * vals/functions so the same immutable instances are reused across all layouts and across
 * every keystroke — the layout tables never allocate keys at runtime.
 *
 * The "bottom row" (symbols toggle, language switch, comma, space, period, enter) is
 * identical in structure for all alphabetic layouts; only the space label changes per
 * language, so it is built by a small factory rather than duplicated.
 */
internal object SharedKeys {

    val SHIFT = Key(
        label = "",
        action = KeyAction.Shift,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Shift",
    )

    val BACKSPACE = Key(
        label = "",
        action = KeyAction.Backspace,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Delete",
        repeatable = true,
    )

    val TO_SYMBOLS = Key(
        label = "?123",
        action = KeyAction.ToSymbols,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Symbols",
    )

    val TO_ALPHA = Key(
        label = "ABC",
        action = KeyAction.ToAlpha,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Letters",
    )

    val TOGGLE_SYMBOLS = Key(
        label = "=\\<",
        action = KeyAction.ToggleSymbolsPage,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "More symbols",
    )

    val LANGUAGE = Key(
        label = "",
        action = KeyAction.SwitchLanguage,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Switch language",
    )

    val EMOJI = Key(
        label = "",
        action = KeyAction.Emoji,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Emoji",
    )

    val ENTER = Key(
        label = "",
        action = KeyAction.Enter,
        weight = 1.5f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Enter",
    )

    val COMMA = Key(
        label = ",",
        action = KeyAction.Character(','),
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        longPressChars = listOf('!', '?', '\'', ':', ';'),
    )

    /** Replaces the comma on email fields, where "@" is far more useful than a comma. */
    val AT = Key(
        label = "@",
        action = KeyAction.Character('@'),
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        longPressChars = listOf(',', '.', '_', '-'),
    )

    val PERIOD = Key(
        label = ".",
        action = KeyAction.Character('.'),
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        longPressChars = listOf('!', '?', ',', '@', '#', '/', '\\'),
    )

    /** The number row digits 1..0 with their long-press fraction/superscript variants. */
    val NUMBER_ROW: KeyRow = KeyRow(
        listOf(
            Key.symbol('1', longPress = "¹½⅓¼"),
            Key.symbol('2', longPress = "²⅔"),
            Key.symbol('3', longPress = "³¾⅜"),
            Key.symbol('4', longPress = "⁴"),
            Key.symbol('5', longPress = "⁵⅝"),
            Key.symbol('6', longPress = "⁶"),
            Key.symbol('7', longPress = "⁷⅞"),
            Key.symbol('8', longPress = "⁸"),
            Key.symbol('9', longPress = "⁹"),
            Key.symbol('0', longPress = "ⁿ∅"),
        ),
    )

    /**
     * Builds the alphabetic bottom row for a language. Order: ?123, comma, language, space,
     * period, enter. On email fields the comma is replaced by "@" ([emailField]). The
     * spacebar shows the language name to confirm the active language.
     */
    fun bottomRow(spaceLabel: String, emailField: Boolean = false): KeyRow = KeyRow(
        listOf(
            TO_SYMBOLS,
            if (emailField) AT else COMMA,
            LANGUAGE,
            Key(
                label = spaceLabel,
                action = KeyAction.Space,
                weight = 4f,
                style = KeyStyle.SPACEBAR,
                contentDescription = "Space",
                repeatable = true,
            ),
            PERIOD,
            ENTER,
        ),
    )
}
