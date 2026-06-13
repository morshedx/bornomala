package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyStyle
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * Calculator-style numeric pad: a 3x3 digit grid with a math-symbol column on the left and
 * edit/action keys on the right, plus a bottom row to return to letters. Opened from the
 * toolbar's number button. Built once as an immutable constant — shared across languages.
 */
internal object NumpadLayout {

    // Every cell is weight 1 so the 5 columns line up across all 4 rows (no size mismatch).
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
    private val space = Key(
        label = "",
        action = KeyAction.Space,
        weight = 1f,
        style = KeyStyle.SPACEBAR,
        contentDescription = "Space",
        repeatable = true,
    )

    val PAD: KeyboardLayout = KeyboardLayout(
        id = "numpad",
        rows = listOf(
            KeyRow(listOf(sym('+'), digit('1'), digit('2'), digit('3'), sym('%'))),
            KeyRow(listOf(sym('-'), digit('4'), digit('5'), digit('6'), sym('/'))),
            KeyRow(listOf(sym('*'), digit('7'), digit('8'), digit('9'), backspace)),
            KeyRow(listOf(abc, space, digit('0'), sym('.'), enter)),
        ),
    )
}
