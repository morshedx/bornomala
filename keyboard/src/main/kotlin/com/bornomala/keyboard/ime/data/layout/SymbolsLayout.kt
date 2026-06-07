package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyAction
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyStyle
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * The two symbol pages, shared across languages. Page 1 carries digits and the most-used
 * punctuation; page 2 carries maths, currency, and bracket variants. Both end in a bottom
 * row whose first key returns to letters ("ABC") and whose second toggles between the two
 * symbol pages ("=\<" / "?123").
 */
internal object SymbolsLayout {

    private fun symbolBottomRow(toggleLabel: String): KeyRow = KeyRow(
        listOf(
            SharedKeys.TO_ALPHA,
            Key(
                label = toggleLabel,
                action = KeyAction.ToggleSymbolsPage,
                weight = 1.5f,
                style = KeyStyle.FUNCTIONAL,
                contentDescription = "Toggle symbols page",
            ),
            SharedKeys.COMMA,
            Key(
                label = "",
                action = KeyAction.Space,
                weight = 4f,
                style = KeyStyle.SPACEBAR,
                contentDescription = "Space",
                repeatable = true,
            ),
            SharedKeys.PERIOD,
            SharedKeys.ENTER,
        ),
    )

    val PAGE_ONE: KeyboardLayout = KeyboardLayout(
        id = "symbols_1",
        rows = listOf(
            KeyRow(
                listOf(
                    Key.symbol('1'),
                    Key.symbol('2'),
                    Key.symbol('3'),
                    Key.symbol('4'),
                    Key.symbol('5'),
                    Key.symbol('6'),
                    Key.symbol('7'),
                    Key.symbol('8'),
                    Key.symbol('9'),
                    Key.symbol('0'),
                ),
            ),
            KeyRow(
                listOf(
                    Key.symbol('@'),
                    Key.symbol('#'),
                    Key.symbol('$', longPress = "€£¥₹৳¢"),
                    Key.symbol('_'),
                    Key.symbol('&'),
                    Key.symbol('-', longPress = "–—•"),
                    Key.symbol('+', longPress = "±"),
                    Key.symbol('(', longPress = "[{<"),
                    Key.symbol(')', longPress = "]}>"),
                    Key.symbol('/'),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.TOGGLE_SYMBOLS,
                    Key.symbol('*'),
                    Key.symbol('"', longPress = "“”«»"),
                    Key.symbol('\'', longPress = "‘’"),
                    Key.symbol(':'),
                    Key.symbol(';'),
                    Key.symbol('!'),
                    Key.symbol('?', longPress = "¿"),
                    SharedKeys.BACKSPACE,
                ),
            ),
            symbolBottomRow(toggleLabel = "=\\<"),
        ),
    )

    val PAGE_TWO: KeyboardLayout = KeyboardLayout(
        id = "symbols_2",
        rows = listOf(
            KeyRow(
                listOf(
                    Key.symbol('~'),
                    Key.symbol('`'),
                    Key.symbol('|'),
                    Key.symbol('•'),
                    Key.symbol('√'),
                    Key.symbol('π'),
                    Key.symbol('÷'),
                    Key.symbol('×'),
                    Key.symbol('¶'),
                    Key.symbol('∆'),
                ),
            ),
            KeyRow(
                listOf(
                    Key.symbol('£'),
                    Key.symbol('¢'),
                    Key.symbol('€'),
                    Key.symbol('¥'),
                    Key.symbol('৳'),
                    Key.symbol('^'),
                    Key.symbol('°'),
                    Key.symbol('='),
                    Key.symbol('{'),
                    Key.symbol('}'),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.TOGGLE_SYMBOLS,
                    Key.symbol('\\'),
                    Key.symbol('©'),
                    Key.symbol('®'),
                    Key.symbol('™'),
                    Key.symbol('%'),
                    Key.symbol('['),
                    Key.symbol(']'),
                    SharedKeys.BACKSPACE,
                ),
            ),
            symbolBottomRow(toggleLabel = "?123"),
        ),
    )
}
