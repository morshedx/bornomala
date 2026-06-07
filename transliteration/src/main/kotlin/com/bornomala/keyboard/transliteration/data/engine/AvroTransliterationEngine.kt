package com.bornomala.keyboard.transliteration.data.engine

import com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngine
import com.bornomala.keyboard.transliteration.domain.model.TransliterationResult

/**
 * [TransliterationEngine] backed by the official OmicronLab Avro Phonetic ruleset via
 * [AvroParser].
 *
 * Strategy — re-derive on every change:
 *  - The only mutable state is a Latin [buffer] (a reused [StringBuilder]; no per-keystroke
 *    list/map allocation).
 *  - [processInput] appends, [delete] drops the last Latin char, then the entire Bangla word
 *    is recomposed from the buffer by [AvroParser.parse]. Recomposing from scratch is what
 *    makes backspace correct for matras and conjuncts — there is no fragile incremental-undo.
 *
 * Not thread safe by design; the IME drives it from a single input thread. The shared
 * [AvroParser] itself is stateless and thread-safe.
 *
 * @param parser the shared, pre-built Avro parser (the rule dictionary is loaded once).
 */
class AvroTransliterationEngine(
    private val parser: AvroParser,
) : TransliterationEngine {

    private val buffer = StringBuilder(MAX_WORD_HINT)

    override fun processInput(input: String): TransliterationResult {
        if (input.isEmpty()) return snapshot()
        buffer.append(input)
        return snapshot()
    }

    override fun delete(): TransliterationResult {
        if (buffer.isEmpty()) return TransliterationResult.EMPTY
        buffer.deleteCharAt(buffer.length - 1)
        return snapshot()
    }

    override fun reset() {
        buffer.setLength(0)
    }

    private fun snapshot(): TransliterationResult {
        if (buffer.isEmpty()) return TransliterationResult.EMPTY
        val raw = buffer.toString()
        val composed = parser.parse(raw)
        return TransliterationResult(
            rawInput = raw,
            composed = composed,
            commitCandidate = composed,
            isComposing = true,
        )
    }

    private companion object {
        const val MAX_WORD_HINT = 32
    }
}
