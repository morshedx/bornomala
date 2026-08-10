package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyIcon
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyStyle
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * Gboard-style numeric pad. Layout (left → right):
 *  - a vertically-scrollable strip of symbol keys ([symbolStrip]) on the left;
 *  - a 3×3 digit grid (1-9) in the centre;
 *  - a right column: %, space (compact glyph), backspace;
 *  - a full-width bottom row: ABC, comma, !?#, 0, =, period, enter.
 *
 * Sizing/colour to match Gboard: the LEFT strip and the RIGHT column are the same size
 * ([SIDE_W]) and share the FUNCTIONAL colour; the digits are wider (weight 1f) and use the
 * normal key colour. The bottom row mixes widths — comma/period are narrow, !?#/= medium,
 * ABC/0 wide. The strip's column width ([SIDE_W] mapped in the renderer) equals a right-column
 * key so both rails read identical.
 */
internal object NumpadLayout {

    // Column model: 7 columns per row — left rail (1) + right rail (1) = 2, and the middle 5
    // columns split equally across the 3 digits, so each digit = 5/3 columns. The renderer gives
    // the left strip column weight 1 and the digit grid weight 6 (3 digits @5/3 + right rail @1).
    private const val DIGIT_W = 5f / 3f
    private const val RAIL_W = 1f

    // Bottom-row punctuation: a pair sits under one digit column, so each is half a digit.
    private const val HALF_DIGIT = DIGIT_W / 2f

    // Left strip symbols: FUNCTIONAL colour, full-size (one per row, the rest scroll).
    private fun stripSym(c: Char): Key =
        Key(label = c.toString(), action = KeyAction.Character(c), style = KeyStyle.FUNCTIONAL)

    private fun digit(c: Char): Key =
        Key(label = c.toString(), action = KeyAction.Character(c), weight = DIGIT_W)

    // Right column — 1 unit, same width + colour as the left strip.
    private val percent = Key(
        label = "%", action = KeyAction.Character('%'), weight = RAIL_W, style = KeyStyle.FUNCTIONAL,
    )
    private val space = Key(
        label = "", action = KeyAction.Space, weight = RAIL_W, style = KeyStyle.FUNCTIONAL,
        contentDescription = "Space", cursorControl = true, iconOverride = KeyIcon.SPACE,
    )
    private val backspace = Key(
        label = "", action = KeyAction.Backspace, weight = RAIL_W, style = KeyStyle.FUNCTIONAL,
        contentDescription = "Delete", repeatable = true,
    )

    // Bottom row — 7 keys summing to 8 units; only 0 is digit-width (2), the rest 1, so every
    // key lines up with a column above (0 under digit 8).
    // Bottom row aligns to the digit columns: ABC under left rail (1), comma+!?# under digit 1
    // (½ digit each), 0 under digit 2 (full digit), =+. under digit 3 (½ each), enter under right
    // rail (1). Punctuation (comma/period) use the rail colour; !?#, 0, = use the digit colour.
    private val abc = Key("ABC", KeyAction.ToAlpha, weight = RAIL_W, style = KeyStyle.FUNCTIONAL, contentDescription = "Letters")
    private val comma = Key(",", KeyAction.Character(','), weight = HALF_DIGIT, style = KeyStyle.FUNCTIONAL)
    private val toSymbols = Key("!?#", KeyAction.ToSymbols, weight = HALF_DIGIT, style = KeyStyle.NORMAL, contentDescription = "Symbols")
    private val zero = Key("0", KeyAction.Character('0'), weight = DIGIT_W, style = KeyStyle.NORMAL)
    private val equals = Key("=", KeyAction.Character('='), weight = HALF_DIGIT, style = KeyStyle.NORMAL)
    private val period = Key(".", KeyAction.Character('.'), weight = HALF_DIGIT, style = KeyStyle.FUNCTIONAL)
    private val enter = Key("", KeyAction.Enter, weight = RAIL_W, style = KeyStyle.FUNCTIONAL, contentDescription = "Enter")

    // Vertically-scrollable left strip; all insert their character.
    private val symbolStrip: List<Key> = listOf(
        stripSym('+'), stripSym('-'), stripSym('*'), stripSym('/'), stripSym('('),
        stripSym(')'), stripSym('='), stripSym('%'), stripSym('<'), stripSym('>'),
    )

    val PAD: KeyboardLayout = KeyboardLayout(
        id = "numpad",
        scrollableLeftStrip = symbolStrip,
        rows = listOf(
            KeyRow(listOf(digit('1'), digit('2'), digit('3'), percent)),
            KeyRow(listOf(digit('4'), digit('5'), digit('6'), space)),
            KeyRow(listOf(digit('7'), digit('8'), digit('9'), backspace)),
            KeyRow(listOf(abc, comma, toSymbols, zero, equals, period, enter)),
        ),
    )
}
