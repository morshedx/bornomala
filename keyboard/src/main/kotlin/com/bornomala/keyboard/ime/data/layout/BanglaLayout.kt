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
            SharedKeys.bottomRow(spaceLabel = "বাংলা (অভ্র)", period = SharedKeys.DARI),
        ),
    )
}
