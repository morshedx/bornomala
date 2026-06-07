package com.bornomala.keyboard.ime.data.layout

import com.bornomala.keyboard.ime.domain.model.Key
import com.bornomala.keyboard.ime.domain.model.KeyRow
import com.bornomala.keyboard.ime.domain.model.KeyboardLayout

/**
 * Bangla (Avro phonetic) layout. Avro phonetic is a *latin-input* method: the user types
 * roman letters (e.g. "ami") which the [com.bornomala.keyboard.ime.domain.port.TransliterationPort]
 * renders to Bangla ("আমি") in the composing region. Therefore the alpha page is a QWERTY
 * of latin keys — the same physical layout as English — but every character keystroke is
 * routed through the transliteration engine while a word is composing.
 *
 * Keeping the latin QWERTY (rather than a fixed Bangla glyph map) is exactly what makes
 * Avro-style typing work and matches the spec's examples (ami, bangladesh, kemon, ...).
 * A dedicated phonetic punctuation set is offered via long-press on the period key in
 * the shared bottom row. The spacebar reads "বাংলা" to signal the active language.
 */
internal object BanglaLayout {

    val AVRO_PHONETIC: KeyboardLayout = KeyboardLayout(
        id = "bn_avro_phonetic",
        rows = listOf(
            KeyRow(
                listOf(
                    Key.letter('q', hint = "1"),
                    Key.letter('w', hint = "2"),
                    Key.letter('e', hint = "3"),
                    Key.letter('r', hint = "4"),
                    Key.letter('t', hint = "5"),
                    Key.letter('y', hint = "6"),
                    Key.letter('u', hint = "7"),
                    Key.letter('i', hint = "8"),
                    Key.letter('o', hint = "9"),
                    Key.letter('p', hint = "0"),
                ),
            ),
            KeyRow(
                listOf(
                    Key.letter('a'),
                    Key.letter('s'),
                    Key.letter('d'),
                    Key.letter('f'),
                    Key.letter('g'),
                    Key.letter('h'),
                    Key.letter('j'),
                    Key.letter('k'),
                    Key.letter('l'),
                ),
            ),
            KeyRow(
                listOf(
                    SharedKeys.SHIFT,
                    Key.letter('z'),
                    Key.letter('x'),
                    Key.letter('c'),
                    Key.letter('v'),
                    Key.letter('b'),
                    Key.letter('n'),
                    Key.letter('m'),
                    SharedKeys.BACKSPACE,
                ),
            ),
            SharedKeys.bottomRow(spaceLabel = "বাংলা"),
        ),
    )
}
