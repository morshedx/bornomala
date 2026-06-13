package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyIcon
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyStyle
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * Gboard-style numeric pad. Layout (left → right):
 *  - a narrow, vertically-scrollable strip of math/symbol keys ([SYMBOL_STRIP]) — only a few
 *    are visible at once, the rest scroll into view; it spans the three digit rows;
 *  - a 3×3 digit grid (1-9) in the centre;
 *  - a narrow right column: %, space (compact glyph), backspace;
 *  - a full-width bottom row: ABC, comma, !?#, 0, =, period, enter.
 *
 * The scrollable strip lives in [KeyboardLayout.scrollableLeftStrip]; the three digit rows and
 * the bottom row live in [KeyboardLayout.rows]. The renderer pins the strip to the left of the
 * digit rows and lays the last row out full-width beneath them. Built once as an immutable
 * constant — shared across languages.
 */
internal object NumpadLayout {

    // Each digit / right-column / strip cell is weight 1 so the columns line up across rows.
    private fun sym(c: Char): Key =
        Key(label = c.toString(), action = KeyAction.Character(c), weight = 1f, style = KeyStyle.FUNCTIONAL)

    private fun digit(c: Char): Key =
        Key(label = c.toString(), action = KeyAction.Character(c), weight = 1f)

    private val backspace = Key(
        label = "",
        action = KeyAction.Backspace,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Delete",
        repeatable = true,
    )
    private val enter = Key(
        label = "",
        action = KeyAction.Enter,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Enter",
    )
    private val abc = Key(
        label = "ABC",
        action = KeyAction.ToAlpha,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Letters",
    )
    private val toSymbols = Key(
        label = "!?#",
        action = KeyAction.ToSymbols,
        weight = 1f,
        style = KeyStyle.FUNCTIONAL,
        contentDescription = "Symbols",
    )
    // Shares KeyAction.Space with the main spacebar, but renders the compact space glyph via the
    // icon override — scoped to this key only, so the main spacebar keeps its own rendering.
    private val space = Key(
        label = "",
        action = KeyAction.Space,
        weight = 1f,
        style = KeyStyle.SPACEBAR,
        contentDescription = "Space",
        repeatable = true,
        iconOverride = KeyIcon.SPACE,
    )

    // The vertically-scrollable left strip: all insert their character. Order matches Gboard's
    // calculator-style symbol set; only the first few are visible, the rest scroll into view.
    private val symbolStrip: List<Key> = listOf(
        sym('+'), sym('-'), sym('*'), sym('/'), sym('('),
        sym(')'), sym('='), sym('%'), sym('<'), sym('>'),
    )

    val PAD: KeyboardLayout = KeyboardLayout(
        id = "numpad",
        scrollableLeftStrip = symbolStrip,
        rows = listOf(
            // Three digit rows; each carries the 3×3 digit grid plus the right function column.
            KeyRow(listOf(digit('1'), digit('2'), digit('3'), sym('%'))),
            KeyRow(listOf(digit('4'), digit('5'), digit('6'), space)),
            KeyRow(listOf(digit('7'), digit('8'), digit('9'), backspace)),
            // Full-width bottom row, rendered beneath the strip + grid.
            KeyRow(listOf(abc, SharedKeys.COMMA, toSymbols, digit('0'), sym('='), SharedKeys.PERIOD, enter)),
        ),
    )
}
