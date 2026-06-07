package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * English QWERTY layout, built once as an immutable constant. Long-press accent sets match
 * common Samsung / Gboard offerings so users can reach diacritics without switching pages.
 * Digit hints on the top row let a long-press surface the matching number too.
 */
internal object EnglishLayout {

    val QWERTY: KeyboardLayout = KeyboardLayout(
        id = "en_qwerty",
        rows = listOf(
            KeyRow(
                listOf(
                    Key.letter('q', hint = "1", longPress = "1"),
                    Key.letter('w', hint = "2", longPress = "2"),
                    Key.letter('e', hint = "3", longPress = "3éèêëē"),
                    Key.letter('r', hint = "4", longPress = "4"),
                    Key.letter('t', hint = "5", longPress = "5"),
                    Key.letter('y', hint = "6", longPress = "6ÿ"),
                    Key.letter('u', hint = "7", longPress = "7úùûü"),
                    Key.letter('i', hint = "8", longPress = "8íìîï"),
                    Key.letter('o', hint = "9", longPress = "9óòôöõ"),
                    Key.letter('p', hint = "0", longPress = "0"),
                ),
            ),
            KeyRow(
                listOf(
                    Key.letter('a', hint = "@", longPress = "@àáâäãåæ"),
                    Key.letter('s', hint = "#", longPress = "#ßś"),
                    Key.letter('d', hint = "$", longPress = "$₹€£¥¢"),
                    Key.letter('f', hint = "%", longPress = "%"),
                    Key.letter('g', hint = "&", longPress = "&"),
                    Key.letter('h', hint = "*", longPress = "*"),
                    Key.letter('j', hint = "-", longPress = "-_"),
                    Key.letter('k', hint = "+", longPress = "+"),
                    Key.letter('l', hint = "(", longPress = "()"),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.SHIFT,
                    Key.letter('z', hint = ")", longPress = ")"),
                    Key.letter('x', hint = "\"", longPress = "\""),
                    Key.letter('c', hint = "'", longPress = "'çć"),
                    Key.letter('v', hint = ":", longPress = ":"),
                    Key.letter('b', hint = ";", longPress = ";"),
                    Key.letter('n', hint = "!", longPress = "!ñ"),
                    Key.letter('m', hint = "?", longPress = "?"),
                    SharedKeys.BACKSPACE,
                ),
            ),
            SharedKeys.bottomRow(spaceLabel = "English"),
        ),
    )
}
