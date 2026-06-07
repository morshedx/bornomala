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
                    SharedKeys.letter('q'),
                    SharedKeys.letter('w'),
                    SharedKeys.letter('e'),
                    SharedKeys.letter('r'),
                    SharedKeys.letter('t'),
                    SharedKeys.letter('y'),
                    SharedKeys.letter('u'),
                    SharedKeys.letter('i'),
                    SharedKeys.letter('o'),
                    SharedKeys.letter('p'),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.letter('a'),
                    SharedKeys.letter('s'),
                    SharedKeys.letter('d'),
                    SharedKeys.letter('f'),
                    SharedKeys.letter('g'),
                    SharedKeys.letter('h'),
                    SharedKeys.letter('j'),
                    SharedKeys.letter('k'),
                    SharedKeys.letter('l'),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.SHIFT,
                    SharedKeys.letter('z'),
                    SharedKeys.letter('x'),
                    SharedKeys.letter('c'),
                    SharedKeys.letter('v'),
                    SharedKeys.letter('b'),
                    SharedKeys.letter('n'),
                    SharedKeys.letter('m'),
                    SharedKeys.BACKSPACE,
                ),
            ),
            SharedKeys.bottomRow(spaceLabel = "English"),
        ),
    )
}
